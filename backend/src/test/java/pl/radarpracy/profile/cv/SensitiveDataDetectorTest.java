package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.profile.cv.SensitiveDataDetector.SensitiveFinding;
import pl.radarpracy.profile.cv.SensitiveDataDetector.SensitiveReport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataDetectorTest {

    private final SensitiveDataDetector detector = new SensitiveDataDetector(null);

    private static final String CV = """
            Anna Nowak
            E-mail: anna.nowak@example.com | Telefon: +48 600 100 200
            Adres: ul. Marszałkowska 10/5, 00-590 Warszawa
            Data urodzenia: 12.03.1995 | Stan cywilny: panna
            PESEL: 95031212342
            Seria i numer dowodu: ABA 300000
            Imię ojca: Jan
            Obywatelstwo: polskie
            Wyznanie: rzymskokatolickie
            Mam dwoje dzieci w wieku szkolnym.
            [ZDJĘCIE]
            Posiadam orzeczenie o umiarkowanym stopniu niepełnosprawności.

            DOŚWIADCZENIE
            Java Developer — PayFlow (2022 – obecnie)
            """;

    private static List<String> categories(SensitiveReport r) {
        return r.findings().stream().map(SensitiveFinding::category).toList();
    }

    @Test
    void detectsAllCategoriesWithVerbatimQuotes() {
        SensitiveReport r = detector.detect(CV);
        assertThat(categories(r)).contains("PESEL", "ID_DOCUMENT", "BIRTH_DATE", "MARITAL_STATUS", "ADDRESS",
                "PARENTS", "NATIONALITY", "RELIGION", "CHILDREN", "PHOTO", "HEALTH");
        for (SensitiveFinding f : r.findings()) {
            assertThat(CV).as(f.category()).contains(f.quote());
            assertThat(f.why()).isNotBlank();
            assertThat(f.suggestion()).isNotBlank();
        }
        assertThat(quote(r, "ADDRESS")).isEqualTo("Adres: ul. Marszałkowska 10/5, 00-590 Warszawa");
        assertThat(quote(r, "BIRTH_DATE")).isEqualTo("Data urodzenia: 12.03.1995");
        assertThat(quote(r, "MARITAL_STATUS")).isEqualTo("Stan cywilny: panna");
        assertThat(quote(r, "PESEL")).isEqualTo("PESEL: 95031212342");
        assertThat(quote(r, "ID_DOCUMENT")).isEqualTo("Seria i numer dowodu: ABA 300000");
        assertThat(r.summary()).contains("szczególnie ryzykowne");
    }

    @Test
    void findingsAreOrderedBySeverityAndHealthIsCandidatesDecision() {
        SensitiveReport r = detector.detect(CV);
        assertThat(r.findings().get(0).severity()).isEqualTo(SensitiveDataDetector.HIGH);
        assertThat(r.findings().get(r.findings().size() - 1).severity()).isEqualTo(SensitiveDataDetector.INFO);
        SensitiveFinding health = r.findings().stream().filter(f -> f.category().equals("HEALTH")).findFirst()
                .orElseThrow();
        assertThat(health.severity()).isEqualTo(SensitiveDataDetector.INFO);
        assertThat(health.why()).contains("Twoja decyzja");
        assertThat(r.findings().stream().filter(f -> f.category().equals("PESEL")).findFirst().orElseThrow().why())
                .contains("art. 22¹");
    }

    @Test
    void peselChecksum() {
        assertThat(SensitiveDataDetector.isValidPesel("44051401359")).isTrue();
        assertThat(SensitiveDataDetector.isValidPesel("95031212342")).isTrue();
        assertThat(SensitiveDataDetector.isValidPesel("44051401358")).isFalse();
        assertThat(SensitiveDataDetector.isValidPesel("95031212345")).isFalse();
        // poprawna suma, ale niemożliwa data (miesiąc 13)
        assertThat(SensitiveDataDetector.isValidPesel("95130000000")).isFalse();
        assertThat(SensitiveDataDetector.isValidPesel("1234")).isFalse();
        assertThat(SensitiveDataDetector.isValidPesel(null)).isFalse();
    }

    @Test
    void idCardChecksum() {
        assertThat(SensitiveDataDetector.isValidIdCard("ABA300000")).isTrue();
        assertThat(SensitiveDataDetector.isValidIdCard("ABA300001")).isFalse();
        assertThat(SensitiveDataDetector.isValidIdCard("AB3000000")).isFalse();
    }

    @Test
    void bareNumbersNeedValidChecksum() {
        assertThat(categories(detector.detect("Kontakt 44051401359"))).containsExactly("PESEL");
        assertThat(categories(detector.detect("Numer zamówienia 44051401358"))).isEmpty();
        // telefon w formacie międzynarodowym bez spacji to nie PESEL
        assertThat(categories(detector.detect("Tel. +48600100200"))).isEmpty();
        assertThat(categories(detector.detect("Dokument ABA300000"))).containsExactly("ID_DOCUMENT");
        assertThat(categories(detector.detect("Kod ABC123456"))).isEmpty();
    }

    @Test
    void cleanCvHasNoFindingsAndCommonWordsAreNotFlagged() {
        SensitiveReport r = detector.detect(CvAuditServiceTest.GOOD_CV);
        assertThat(r.findings()).isEmpty();
        assertThat(r.summary()).isEqualTo("Nie znaleziono zbędnych danych wrażliwych.");
        String tricky = """
                Mam 10 lat doświadczenia w obsłudze klienta.
                Zainteresowania: fotografia, góry, Adobe Photoshop.
                Ukończyłam Katolicki Uniwersytet Lubelski.
                Projekt dostępności dla osób z niepełnosprawnościami (WCAG 2.2).
                Adres e-mail: ewa1990@example.com
                Aplikacja dla rodziców i dzieci w wieku szkolnym.
                Born to code? Nie, ale lubię to.
                """;
        assertThat(detector.detect(tricky).findings()).isEmpty();
    }

    @Test
    void ageAndEnglishVariants() {
        assertThat(categories(detector.detect("Wiek: 29"))).containsExactly("BIRTH_DATE");
        assertThat(categories(detector.detect("Mam 29 lat."))).containsExactly("BIRTH_DATE");
        assertThat(categories(detector.detect("Date of birth: 1990-05-01"))).containsExactly("BIRTH_DATE");
        assertThat(categories(detector.detect("Marital status: married"))).containsExactly("MARITAL_STATUS");
        assertThat(categories(detector.detect("Nationality: Polish"))).containsExactly("NATIONALITY");
        assertThat(categories(detector.detect("ur. 01.02.1990 w Krakowie"))).containsExactly("BIRTH_DATE");
        assertThat(categories(detector.detect("Zdjęcie: w załączeniu"))).containsExactly("PHOTO");
        assertThat(categories(detector.detect("Stan zdrowia: bardzo dobry"))).containsExactly("HEALTH");
        assertThat(categories(detector.detect("Nazwisko rodowe: Kowalska"))).containsExactly("PARENTS");
        assertThat(categories(detector.detect("al. Jerozolimskie 100 m. 5, 00-001 Warszawa")))
                .containsExactly("ADDRESS");
    }

    private static String quote(SensitiveReport r, String category) {
        return r.findings().stream().filter(f -> f.category().equals(category)).map(SensitiveFinding::quote)
                .findFirst().orElseThrow();
    }
}

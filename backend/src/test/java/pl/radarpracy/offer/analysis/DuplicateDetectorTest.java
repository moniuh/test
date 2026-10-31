package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DuplicateDetectorTest {

    static final String LOGITECH = """
            Java Developer
            LogiTech Solutions — Kraków / 100% zdalnie
            Umowa o pracę, 14 000 – 18 000 zł brutto miesięcznie

            Twoje zadania:
            - Rozwój systemu TMS do zarządzania transportem
            - Integracje REST z systemami przewoźników

            Wymagania:
            - 3 lata doświadczenia w Java
            - Spring Framework, Hibernate
            - Dobra znajomość SQL
            - REST API
            - Git
            - Komunikatywny angielski

            Mile widziane:
            - Docker
            - Angular

            Oferujemy: karta sportowa, opieka medyczna, PPK, praca zdalna.
            """;

    static final String LOGITECH_DUP = """
            Java Developer (Mid) — LogiTech Solutions sp. z o.o.
            Kraków, praca w pełni zdalna. UoP: 14 000 – 18 000 PLN brutto/mies.

            Czym będziesz się zajmować:
            - rozwój naszego systemu TMS (zarządzanie transportem)
            - integracje REST API z przewoźnikami

            Wymagania:
            - min. 3 lata doświadczenia z Java
            - Spring, Hibernate
            - SQL
            - REST API, Git
            - angielski komunikatywny

            Mile widziane:
            - Docker, Angular

            Benefity: karta sportowa, PPK.
            """;

    static final String RETAIL = """
            Java Developer — RetailOne
            Warszawa, hybrydowo. UoP 13 000 – 16 000 zł brutto.

            Wymagania:
            - 2 lata doświadczenia w Java
            - Spring Boot
            - SQL
            - Git
            """;

    @Test
    void normalizesCompanyLegalForms() {
        assertThat(DuplicateDetector.normalizeCompany("LogiTech Solutions sp. z o.o.")).isEqualTo("logitech solutions");
        assertThat(DuplicateDetector.normalizeCompany("LOGITECH SOLUTIONS Sp. z o. o.")).isEqualTo("logitech solutions");
        assertThat(DuplicateDetector.normalizeCompany("Bank Polski S.A.")).isEqualTo("bank polski");
        assertThat(DuplicateDetector.normalizeCompany("Nordlys Software AB")).isEqualTo("nordlys software");
        assertThat(DuplicateDetector.normalizeCompany("Żabka Polska sp. j.")).isEqualTo("zabka");
        assertThat(DuplicateDetector.normalizeCompany("Acme GmbH & Co.")).isEqualTo("acme co");
        assertThat(DuplicateDetector.normalizeCompany(null)).isEmpty();
    }

    @Test
    void sameOfferFromTwoSourcesIsDuplicate() {
        var a = DuplicateDetector.fingerprint(2L, "Java Developer", "LogiTech Solutions", LOGITECH, "https://pracuj.pl/a");
        var b = DuplicateDetector.fingerprint(4L, "Java Developer (Mid)", "LogiTech Solutions sp. z o.o.", LOGITECH_DUP,
                "https://justjoin.it/b");
        var s = DuplicateDetector.compare(a, b);
        assertThat(s.company()).isEqualTo(1.0);
        assertThat(s.title()).isEqualTo(1.0);
        assertThat(s.content()).isBetween(0.5, 0.99);
        assertThat(s.score()).isGreaterThanOrEqualTo(DuplicateDetector.THRESHOLD).isLessThanOrEqualTo(1.0);
        assertThat(s.reasons()).contains("ta sama firma", "ten sam tytuł stanowiska");
        // symetria
        assertThat(DuplicateDetector.compare(b, a).score()).isEqualTo(s.score());
    }

    @Test
    void sameTitleAtDifferentCompanyIsNotDuplicate() {
        var a = DuplicateDetector.fingerprint(2L, "Java Developer", "LogiTech Solutions", LOGITECH, null);
        var b = DuplicateDetector.fingerprint(13L, "Java Developer", "RetailOne", RETAIL, null);
        var s = DuplicateDetector.compare(a, b);
        assertThat(s.score()).isLessThan(DuplicateDetector.MIN_SCORE);
        assertThat(s.reasons()).contains("inna firma");
    }

    @Test
    void sameUrlIsAlwaysDuplicate() {
        var a = DuplicateDetector.fingerprint(1L, "A", "X", "treść jeden", "https://www.pracuj.pl/oferta/1?utm=fb");
        var b = DuplicateDetector.fingerprint(2L, "B", "Y", "zupełnie inna", "https://pracuj.pl/oferta/1/");
        var s = DuplicateDetector.compare(a, b);
        assertThat(s.sameUrl()).isTrue();
        assertThat(s.score()).isEqualTo(1.0);
    }

    @Test
    void differentSeniorityInTitlesLowersTitleScore() {
        var a = DuplicateDetector.fingerprint(1L, "Junior Java Developer", "Acme", "Java Spring", null);
        var b = DuplicateDetector.fingerprint(2L, "Senior Java Developer", "Acme", "Java Spring", null);
        var s = DuplicateDetector.compare(a, b);
        assertThat(s.title()).isEqualTo(0.6);
        assertThat(s.reasons()).contains("różny poziom w tytule");
    }

    @Test
    void missingPartsAreSkippedNotCountedAsZero() {
        var a = DuplicateDetector.fingerprint(1L, "Programista Java", null, null, null);
        var b = DuplicateDetector.fingerprint(2L, "Java Developer", null, null, null);
        var s = DuplicateDetector.compare(a, b);
        assertThat(s.company()).isNull();
        assertThat(s.content()).isNull();
        assertThat(s.title()).isEqualTo(1.0); // „programista” ≡ „developer”
        assertThat(s.score()).isEqualTo(1.0);
    }

    @Test
    void reportSortsCandidatesAndMarksDuplicates() {
        JobOffer original = offer(2L, "Java Developer", "LogiTech Solutions", LOGITECH, Instant.parse("2026-10-01T00:00:00Z"));
        JobOffer dup = offer(4L, "Java Developer (Mid)", "LogiTech Solutions sp. z o.o.", LOGITECH_DUP,
                Instant.parse("2026-10-03T00:00:00Z"));
        JobOffer other = offer(13L, "Java Developer", "RetailOne", RETAIL, Instant.parse("2026-09-01T00:00:00Z"));
        DuplicateService service = new DuplicateService(mock(OfferService.class));
        DuplicateReport r = service.report(4L, DuplicateService.fingerprint(dup), dup, List.of(original, dup, other));
        assertThat(r.threshold()).isEqualTo(DuplicateDetector.THRESHOLD);
        assertThat(r.candidates()).extracting(DuplicateReport.Candidate::offerId).containsExactly(2L);
        DuplicateReport.Candidate c = r.candidates().get(0);
        assertThat(c.duplicate()).isTrue();
        assertThat(c.older()).isTrue();
        assertThat(c.alreadyMarked()).isFalse();
    }

    private static JobOffer offer(Long id, String title, String company, String description, Instant created) {
        JobOffer o = new JobOffer();
        o.setId(id);
        o.setTitle(title);
        o.setCompany(company);
        o.setDescription(description);
        o.setCreatedAt(created);
        return o;
    }
}

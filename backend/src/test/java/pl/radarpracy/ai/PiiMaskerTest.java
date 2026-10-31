package pl.radarpracy.ai;

import org.junit.jupiter.api.Test;
import pl.radarpracy.profile.CandidateProfile;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksPersonalDataAndRestoresIt() {
        CandidateProfile p = new CandidateProfile();
        p.setFullName("Anna Nowak");
        String cv = """
                Anna Nowak, anna.nowak@example.com, tel. +48 600 100 200
                ul. Marszałkowska 10/5, 00-590 Warszawa
                PESEL 44051401359, Data urodzenia: 12.03.1995
                github.com/anowak-dev
                Pani Nowak zna Javę.
                """;
        PiiMasker.Masked m = masker.mask(cv, p);
        assertThat(m.text()).doesNotContain("Anna Nowak", "anna.nowak@example.com", "600 100 200", "44051401359",
                "Marszałkowska", "12.03.1995", "anowak-dev", "Nowak");
        assertThat(m.text()).contains("[KANDYDAT]", "[E-MAIL-1]", "[TELEFON-1]", "[PESEL-1]", "[NAZWISKO]");
        assertThat(m.unmask("Pozdrawiam, [KANDYDAT] ([E-MAIL-1])")).isEqualTo("Pozdrawiam, Anna Nowak (anna.nowak@example.com)");
    }

    @Test
    void doesNotMaskInvalidPeselOrShortNumbers() {
        PiiMasker.Masked m = masker.mask("Zespół 12345678901 osób? 2019 – 2021, 40%", null);
        assertThat(m.text()).contains("12345678901", "2019");
    }
}

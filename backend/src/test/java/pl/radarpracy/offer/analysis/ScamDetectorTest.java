package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.analysis.ScamAssessment.RiskLevel;
import pl.radarpracy.salary.SalaryPeriod;

import static org.assertj.core.api.Assertions.assertThat;

class ScamDetectorTest {

    static final String SCAM = """
            PRACA ZDALNA!!! 500 zł DZIENNIE bez doświadczenia
            Szukamy osób do przyjmowania paczek w domu i przesyłania ich dalej pod wskazany adres.

            Wymagania:
            - Pełnoletność
            - Dostęp do telefonu

            Jak zacząć:
            - Wymagana jednorazowa opłata 199 zł za pakiet startowy i szkolenie
            - Prześlij skan dowodu osobistego oraz numer konta bankowego, abyśmy mogli przelać zaliczkę
            - Kontakt wyłącznie przez WhatsApp lub Telegram
            - Możliwa wypłata w kryptowalutach
            """;

    private static JobOffer offer(String title, String company, String description) {
        JobOffer o = new JobOffer();
        o.setTitle(title);
        o.setCompany(company);
        o.setDescription(description);
        return o;
    }

    @Test
    void scamOfferIsHighRiskWithAllSignals() {
        JobOffer o = offer("Praca zdalna — przyjmowanie paczek, 500 zł dziennie", null, SCAM);
        o.setSalaryMin(500);
        o.setSalaryPeriod(SalaryPeriod.DAY);
        ScamAssessment a = ScamDetector.assess(o);
        assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(a.levelLabel()).isEqualTo("wysokie");
        assertThat(a.score()).isEqualTo(100);
        assertThat(a.signals()).extracting(ScamAssessment.Signal::code).contains("UPFRONT_FEE", "PARCEL_MULE",
                "MONEY_TRANSFER", "ID_DOCUMENTS", "MESSENGER_ONLY", "CRYPTO", "UNREALISTIC_PAY", "NO_COMPANY", "PRESSURE");
        for (ScamAssessment.Signal s : a.signals()) {
            assertThat(s.points()).isPositive();
            if (s.quote() != null) {
                assertThat(SCAM + o.getTitle()).as(s.code()).contains(s.quote());
            }
        }
        assertThat(a.recommendations()).anyMatch(r -> r.contains("KRS")).anyMatch(r -> r.contains("PESEL"))
                .anyMatch(r -> r.contains("Nie płać"));
    }

    @Test
    void scoreIsSumOfPointsCappedAt100() {
        JobOffer o = offer("Kurier", "Acme", "Kontakt wyłącznie przez WhatsApp. Wypłata w bitcoinach.");
        ScamAssessment a = ScamDetector.assess(o);
        assertThat(a.score()).isEqualTo(25 + 20);
        assertThat(a.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(a.mediumThreshold()).isEqualTo(ScamDetector.MEDIUM);
        assertThat(a.highThreshold()).isEqualTo(ScamDetector.HIGH);
    }

    @Test
    void legitimateOfferIsLowRisk() {
        String text = """
                Java Developer — LogiTech Solutions
                Umowa o pracę, 14 000 – 18 000 zł brutto. Wynagrodzenie przelewem na konto do 10. dnia miesiąca.
                Oferujemy: dofinansowanie opłat za certyfikaty, budżet szkoleniowy, pakiet medyczny.
                Kontakt: rekrutacja@logitech.example
                Dane osobowe (imię, PESEL) zbierzemy przy podpisaniu umowy.
                """;
        ScamAssessment a = ScamDetector.assess(offer("Java Developer", "LogiTech Solutions", text));
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
        assertThat(a.score()).isZero();
        assertThat(a.signals()).isEmpty();
        assertThat(a.recommendations()).hasSize(1).first().asString().contains("Nie wykryto");
    }

    @Test
    void warehouseParcelsAreNotMuleAndBlockchainJobIsNotCryptoPay() {
        String text = """
                Magazynier — Logistyka sp. z o.o.
                Przyjmowanie i wydawanie paczek w magazynie w Strykowie.
                Mile widziane doświadczenie z kryptowalutami i blockchain.
                """;
        ScamAssessment a = ScamDetector.assess(offer("Magazynier", "Logistyka sp. z o.o.", text));
        assertThat(a.signals()).extracting(ScamAssessment.Signal::code).doesNotContain("PARCEL_MULE", "CRYPTO");
    }

    @Test
    void singleCriticalSignalGivesAtLeastMedium() {
        ScamAssessment a = ScamDetector.assess(offer("Asystent", "Firma X",
                "Przed rozmową prześlij skan dowodu osobistego na adres e-mail."));
        assertThat(a.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(a.signals()).extracting(ScamAssessment.Signal::code).containsExactly("ID_DOCUMENTS");
    }

    @Test
    void messengerMentionWithoutOnlyIsMinorSignal() {
        ScamAssessment a = ScamDetector.assess(offer("Konsultant", "Firma X",
                "Masz pytania? Napisz na WhatsApp albo zadzwoń: 22 123 45 67."));
        assertThat(a.signals()).extracting(ScamAssessment.Signal::code).containsExactly("MESSENGER");
        assertThat(a.score()).isEqualTo(10);
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void riskLevelSerializedLowercase() {
        assertThat(RiskLevel.MEDIUM.json()).isEqualTo("medium");
        assertThat(RiskLevel.MEDIUM.label()).isEqualTo("średnie");
    }
}

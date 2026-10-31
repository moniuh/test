package pl.radarpracy.salary.tools;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Integracja z modułem analizy ofert: oferty z wysokim ryzykiem oszustwa nie zniekształcają statystyk płac. */
class SalaryStatsScamExclusionTest {

    private final SalaryStatsService service = new SalaryStatsService(null, new SalaryNormalizer(SalaryProperties.defaults()));

    private static JobOffer offer(long id, String title, String description, int min, int max) {
        JobOffer o = new JobOffer();
        o.setId(id);
        o.setTitle(title);
        o.setDescription(description);
        o.setSalaryMin(min);
        o.setSalaryMax(max);
        o.setSalaryPeriod(SalaryPeriod.MONTH);
        o.setSalaryGross(true);
        o.setContractTypes(List.of(ContractType.UOP));
        return o;
    }

    @Test
    void highRiskScamOffersAreNotSampled() {
        JobOffer normal = offer(1, "Java Developer", "Wymagania: Java, Spring Boot. Umowa o pracę.", 14000, 18000);
        JobOffer scam = offer(2, "Praca zdalna — przyjmowanie paczek", """
                Przyjmowanie paczek w domu i przesyłanie ich dalej. Bez doświadczenia, 500 zł dziennie.
                Wymagana jednorazowa opłata 199 zł za pakiet startowy.
                Prześlij skan dowodu osobistego oraz numer konta bankowego.
                Kontakt wyłącznie przez WhatsApp lub Telegram. Wypłata w kryptowalutach.
                """, 30000, 40000);
        assertThat(service.samples(List.of(normal, scam))).extracting(SalaryStatsService.Sample::offerId)
                .containsExactly(1L);
    }
}

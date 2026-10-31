package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.matching.ranking.Fixtures.SALARY;
import static pl.radarpracy.matching.ranking.Fixtures.offer;
import static pl.radarpracy.matching.ranking.Fixtures.profile;

class DealBreakerFilterTest {

    private static List<Exclusion> check(JobOffer o, CandidateProfile p) {
        return DealBreakerFilter.check(o, p, SALARY.normalize(o));
    }

    private static List<Exclusion.Type> types(JobOffer o, CandidateProfile p) {
        return check(o, p).stream().map(Exclusion::type).toList();
    }

    @Test
    void cleanOfferIsNotExcluded() {
        JobOffer o = offer("Java Developer").location("Warszawa").mode(WorkMode.HYBRID).contracts(ContractType.B2B)
                .salary(20000, 25000).description("Spring Boot, Kafka. Praca w zespole produktowym.").build();
        assertThat(check(o, profile())).isEmpty();
    }

    @Test
    void dealBreakerPhraseIsFoundAfterFoldingWithVerbatimQuote() {
        String description = "Oferujemy:\n- PRACA   Zmianowa w systemie 3-zmianowym\n- Premie";
        JobOffer o = offer("Operator").mode(WorkMode.REMOTE).description(description).build();
        List<Exclusion> ex = check(o, profile());
        assertThat(ex).hasSize(1);
        Exclusion e = ex.get(0);
        assertThat(e.type()).isEqualTo(Exclusion.Type.DEAL_BREAKER);
        assertThat(e.reason()).contains("„praca zmianowa”");
        assertThat(e.evidence()).contains("PRACA   Zmianowa");
        assertThat(OfferTexts.fullContent(o)).contains(e.evidence());
    }

    @Test
    void phraseWithoutDiacriticsMatchesTextWithDiacritics() {
        CandidateProfile p = profile();
        p.setDealBreakers(List.of("dyspozycyjnosc 24/7"));
        JobOffer o = offer("Konsultant").mode(WorkMode.REMOTE).description("Wymagana Dyspozycyjność 24/7.").build();
        assertThat(types(o, p)).containsExactly(Exclusion.Type.DEAL_BREAKER);
    }

    @Test
    void phraseMustStartAtWordBoundaryButMayBeInflected() {
        CandidateProfile p = profile();
        p.setDealBreakers(List.of("uz", "nadgodzin"));
        JobOffer big = offer("Analityk").mode(WorkMode.REMOTE).description("Duże projekty i duży zespół.").build();
        JobOffer overtime = offer("Analityk").mode(WorkMode.REMOTE).description("Gotowość do pracy w nadgodzinach.")
                .build();
        assertThat(check(big, p)).isEmpty();
        assertThat(types(overtime, p)).containsExactly(Exclusion.Type.DEAL_BREAKER);
    }

    @Test
    void phraseInBenefitsAlsoCounts() {
        JobOffer o = offer("Magazynier").mode(WorkMode.REMOTE).benefits("Praca zmianowa (3 zmiany)").build();
        assertThat(types(o, profile())).containsExactly(Exclusion.Type.DEAL_BREAKER);
    }

    @Test
    void workModeOutsideAcceptedIsExcluded() {
        JobOffer o = offer("Java").location("Warszawa").mode(WorkMode.ONSITE).build();
        List<Exclusion> ex = check(o, profile());
        assertThat(ex).extracting(Exclusion::type).containsExactly(Exclusion.Type.WORK_MODE);
        assertThat(ex.get(0).reason()).contains("stacjonarnie").contains("zdalnie, hybrydowo");
    }

    @Test
    void officeInAnotherCityWithoutRelocationIsExcluded() {
        CandidateProfile p = profile();
        JobOffer krakow = offer("Java").location("Kraków").mode(WorkMode.HYBRID).build();
        assertThat(types(krakow, p)).containsExactly(Exclusion.Type.LOCATION);
        assertThat(check(krakow, p).get(0).reason()).contains("Kraków").contains("przeprowadzki");

        JobOffer remoteKrakow = offer("Java").location("Kraków").mode(WorkMode.REMOTE).build();
        assertThat(check(remoteKrakow, p)).isEmpty();

        p.setWillingToRelocate(true);
        assertThat(check(krakow, p)).isEmpty();
    }

    @Test
    void onlyUnacceptedContractsAreExcluded() {
        JobOffer uod = offer("Java").mode(WorkMode.REMOTE).contracts(ContractType.UOD).build();
        JobOffer mixed = offer("Java").mode(WorkMode.REMOTE).contracts(ContractType.UOD, ContractType.UOP).build();
        assertThat(types(uod, profile())).containsExactly(Exclusion.Type.CONTRACT);
        assertThat(check(uod, profile()).get(0).reason()).contains("umowa o dzieło");
        assertThat(check(mixed, profile())).isEmpty();
    }

    @Test
    void upperSalaryBoundBelowMinimumIsExcluded() {
        JobOffer low = offer("Junior").mode(WorkMode.REMOTE).salary(7000, 9000).build();
        JobOffer onlyFrom = offer("Junior").mode(WorkMode.REMOTE).salary(9000, null).build();
        JobOffer upTo = offer("Junior").mode(WorkMode.REMOTE).salary(null, 12000).build();
        List<Exclusion> ex = check(low, profile());
        assertThat(ex).extracting(Exclusion::type).containsExactly(Exclusion.Type.SALARY);
        assertThat(ex.get(0).reason()).contains("9 000").contains("15 000");
        assertThat(check(onlyFrom, profile())).as("„od 9 000” nie ma górnej granicy").isEmpty();
        assertThat(types(upTo, profile())).containsExactly(Exclusion.Type.SALARY);
    }

    @Test
    void emptyPreferencesExcludeNothing() {
        JobOffer o = offer("Java").location("Kraków").mode(WorkMode.ONSITE).contracts(ContractType.UOD)
                .salary(1000, 2000).description("praca zmianowa").build();
        assertThat(check(o, new CandidateProfile())).isEmpty();
    }

    @Test
    void multipleReasonsAreReportedInStableOrder() {
        JobOffer o = offer("Specjalista").location("Wrocław").mode(WorkMode.ONSITE).contracts(ContractType.UOD)
                .salary(5000, 6000).description("Dyspozycyjność 24/7").build();
        assertThat(types(o, profile())).containsExactly(Exclusion.Type.DEAL_BREAKER, Exclusion.Type.WORK_MODE,
                Exclusion.Type.LOCATION, Exclusion.Type.CONTRACT, Exclusion.Type.SALARY);
    }
}

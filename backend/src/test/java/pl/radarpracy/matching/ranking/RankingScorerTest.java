package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.ranking.RankingWeights.Factor;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.radarpracy.matching.ranking.Fixtures.SALARY;
import static pl.radarpracy.matching.ranking.Fixtures.TODAY;
import static pl.radarpracy.matching.ranking.Fixtures.match;
import static pl.radarpracy.matching.ranking.Fixtures.offer;
import static pl.radarpracy.matching.ranking.Fixtures.profile;

class RankingScorerTest {

    private static final LearnedPreferences NOT_LEARNED =
            new PreferenceLearner(new OfferFeatures(new SkillDictionary())).learn(List.of());

    private static RankedOffer score(JobOffer o, MatchResult m, CandidateProfile p) {
        return RankingScorer.score(o, m, SALARY.normalize(o), p, NOT_LEARNED, TODAY);
    }

    private static RankComponent component(RankedOffer r, Factor f) {
        return r.components().stream().filter(c -> c.key().equals(f.key())).findFirst().orElseThrow();
    }

    @Test
    void baseWeightsSumToOneAndCvMatchWeighsMost() {
        double sum = Arrays.stream(Factor.values()).filter(Factor::base).mapToDouble(Factor::weight).sum();
        assertThat(sum).isCloseTo(1.0, within(1e-9));
        assertThat(Arrays.stream(Factor.values()).filter(Factor::base).filter(f -> f != Factor.CV_MATCH))
                .allMatch(f -> f.weight() < Factor.CV_MATCH.weight());
        assertThat(RankingWeights.LEARNING_MAX_POINTS).isEqualTo(10.0);
    }

    @Test
    void perfectFitAddsUpComponentByComponent() {
        JobOffer o = offer("Java Developer").location("Warszawa").mode(WorkMode.REMOTE).contracts(ContractType.B2B)
                .salary(22000, 28000).build();
        RankedOffer r = score(o, match(o.getId(), 80), profile());

        // 0,8×50 + 1×15 (B2B ≈ 17 600–22 400 UoP, środek 20 000 ≥ 1,2 × 15 000) + 10 + 10 + 8 + 7 = 90
        assertThat(r.rankScore()).isEqualTo(90);
        assertThat(r.matchScore()).isEqualTo(80);
        assertThat(r.components()).extracting(RankComponent::key)
                .containsExactly("cv", "salary", "workMode", "location", "freshness", "contract", "learned");
        assertThat(component(r, Factor.CV_MATCH).points()).isEqualTo(40.0);
        assertThat(component(r, Factor.SALARY).value()).isEqualTo(1.0);
        assertThat(r.comparableSalaryMid()).isEqualTo(20000);
        assertThat(r.excluded()).isFalse();
        assertThat(r.components()).allMatch(c -> c.explanation() != null && !c.explanation().isBlank());
    }

    @Test
    void scoringIsDeterministic() {
        JobOffer o = offer("Java Developer").location("Kraków").mode(WorkMode.HYBRID).contracts(ContractType.UOP)
                .salary(14000, 18000).published(TODAY.minusDays(10)).build();
        assertThat(score(o, match(o.getId(), 63), profile())).isEqualTo(score(o, match(o.getId(), 63), profile()));
    }

    @Test
    void missingSalaryIsNeutralWithNote() {
        JobOffer o = offer("Java Developer").mode(WorkMode.REMOTE).build();
        RankComponent c = component(score(o, match(o.getId(), 50), profile()), Factor.SALARY);
        assertThat(c.neutral()).isTrue();
        assertThat(c.value()).isEqualTo(RankingWeights.NEUTRAL);
        assertThat(c.explanation()).contains("nie podaje widełek");
    }

    @Test
    void salaryWithoutMinimumInProfileIsNeutral() {
        CandidateProfile p = profile();
        p.setMinMonthlySalary(null);
        JobOffer o = offer("Java Developer").salary(9000, 10000).build();
        assertThat(component(score(o, match(o.getId(), 50), p), Factor.SALARY).neutral()).isTrue();
    }

    @Test
    void salaryBelowMinimumScoresZero() {
        JobOffer o = offer("Junior").salary(7000, 9000).build();
        RankComponent c = component(score(o, match(o.getId(), 50), profile()), Factor.SALARY);
        assertThat(c.value()).isZero();
        assertThat(c.explanation()).contains("poniżej Twojego minimum");
    }

    @Test
    void salaryCurveIsPiecewiseLinear() {
        assertThat(RankingScorer.salaryCurve(0.7)).isZero();
        assertThat(RankingScorer.salaryCurve(0.9)).isCloseTo(0.3, within(1e-9));
        assertThat(RankingScorer.salaryCurve(1.0)).isCloseTo(0.6, within(1e-9));
        assertThat(RankingScorer.salaryCurve(1.1)).isCloseTo(0.8, within(1e-9));
        assertThat(RankingScorer.salaryCurve(1.5)).isEqualTo(1.0);
    }

    @Test
    void workModeOutsideAcceptedScoresZero() {
        JobOffer o = offer("Java").location("Warszawa").mode(WorkMode.ONSITE).build();
        RankComponent c = component(score(o, match(o.getId(), 50), profile()), Factor.WORK_MODE);
        assertThat(c.value()).isZero();
        assertThat(c.explanation()).contains("Stacjonarnie").contains("zdalnie, hybrydowo");
    }

    @Test
    void locationHandlesRemoteInflectionAndRelocation() {
        CandidateProfile p = profile();
        JobOffer remote = offer("Java").location("Gdańsk").mode(WorkMode.REMOTE).build();
        JobOffer warsaw = offer("Java").location("w Warszawie (2 dni w biurze)").mode(WorkMode.HYBRID).build();
        JobOffer krakow = offer("Java").location("Kraków").mode(WorkMode.HYBRID).build();

        assertThat(component(score(remote, match(remote.getId(), 50), p), Factor.LOCATION).value()).isEqualTo(1.0);
        assertThat(component(score(warsaw, match(warsaw.getId(), 50), p), Factor.LOCATION).value()).isEqualTo(1.0);
        assertThat(component(score(krakow, match(krakow.getId(), 50), p), Factor.LOCATION).value()).isZero();
        p.setWillingToRelocate(true);
        RankComponent relocate = component(score(krakow, match(krakow.getId(), 50), p), Factor.LOCATION);
        assertThat(relocate.value()).isEqualTo(0.5);
        assertThat(relocate.explanation()).contains("przeprowadzkę");
    }

    @Test
    void contractComponent() {
        JobOffer both = offer("Java").contracts(ContractType.UOP, ContractType.UOD).build();
        JobOffer uod = offer("Java").contracts(ContractType.UOD).build();
        JobOffer none = offer("Java").build();
        assertThat(component(score(both, match(both.getId(), 50), profile()), Factor.CONTRACT).value()).isEqualTo(1.0);
        assertThat(component(score(uod, match(uod.getId(), 50), profile()), Factor.CONTRACT).value()).isZero();
        assertThat(component(score(none, match(none.getId(), 50), profile()), Factor.CONTRACT).neutral()).isTrue();
    }

    @Test
    void freshnessDecaysAndExpiredOffersScoreZero() {
        JobOffer fresh = offer("Java").published(TODAY.minusDays(2)).build();
        JobOffer older = offer("Java").published(TODAY.minusDays(16)).build();
        JobOffer expired = offer("Java").published(TODAY.minusDays(40)).expires(TODAY.minusDays(1)).build();
        assertThat(component(score(fresh, match(fresh.getId(), 50), profile()), Factor.FRESHNESS).value()).isEqualTo(1.0);
        RankComponent c = component(score(older, match(older.getId(), 50), profile()), Factor.FRESHNESS);
        assertThat(c.value()).isCloseTo(1 - 13.0 / 42, within(0.001));
        assertThat(c.explanation()).contains("16 dni temu");
        assertThat(component(score(expired, match(expired.getId(), 50), profile()), Factor.FRESHNESS).value()).isZero();
    }

    @Test
    void offerWithoutRequirementsOrCvIsNeutralForCvComponent() {
        JobOffer o = offer("Java").build();
        MatchResult noReqs = new MatchResult(o.getId(), 1L, "CV", 0, 0, 0, 0, 0, List.of(), List.of(), List.of(),
                "Oferta nie ma wymagań do porównania", List.of());
        RankComponent c = component(score(o, noReqs, profile()), Factor.CV_MATCH);
        assertThat(c.neutral()).isTrue();
        assertThat(c.value()).isEqualTo(0.5);
        MatchResult noCv = new MatchResult(o.getId(), null, null, 0, 0, 2, 0, 0, List.of(), List.of(), List.of(),
                "Dodaj CV", List.of());
        assertThat(component(score(o, noCv, profile()), Factor.CV_MATCH).explanation()).contains("Brak CV");
    }

    @Test
    void learningInactiveIsNeutralAndExplainsThreshold() {
        JobOffer o = offer("Java").build();
        RankComponent c = component(score(o, match(o.getId(), 50), profile()), Factor.LEARNED);
        assertThat(c.points()).isZero();
        assertThat(c.neutral()).isTrue();
        assertThat(c.explanation()).contains("min. 3");
    }

    @Test
    void learnedAdjustmentIsAddedAndBounded() {
        SkillDictionary dictionary = new SkillDictionary();
        List<JobOffer> rated = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rated.add(offer("Kafka Developer").description("Wymagania: Kafka, Java").status(OfferStatus.SAVED).build());
        }
        LearnedPreferences learned = new PreferenceLearner(new OfferFeatures(dictionary)).learn(rated);
        JobOffer o = offer("Backend Developer").description("Kafka i Java").mode(WorkMode.REMOTE).build();
        RankedOffer withLearning = RankingScorer.score(o, match(o.getId(), 60), SALARY.normalize(o), profile(), learned,
                TODAY);
        RankedOffer without = score(o, match(o.getId(), 60), profile());

        RankComponent c = component(withLearning, Factor.LEARNED);
        assertThat(c.points()).isPositive().isLessThanOrEqualTo(RankingWeights.LEARNING_MAX_POINTS);
        assertThat(c.explanation()).contains("lubisz oferty z Kafka");
        assertThat(withLearning.rankScore()).isGreaterThan(without.rankScore());
        assertThat(withLearning.rankScore()).isBetween(0, 100);
    }

    @Test
    void excludedOfferKeepsScoreButCarriesReasons() {
        JobOffer o = offer("Konsultant").description("Wymagana dyspozycyjność 24/7.").mode(WorkMode.REMOTE).build();
        RankedOffer r = score(o, match(o.getId(), 50), profile());
        assertThat(r.excluded()).isTrue();
        assertThat(r.exclusions()).extracting(Exclusion::type).containsExactly(Exclusion.Type.DEAL_BREAKER);
        assertThat(r.rankScore()).isPositive();
    }
}

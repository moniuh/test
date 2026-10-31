package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import pl.radarpracy.matching.ranking.OfferFeatures.Feature;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.matching.ranking.Fixtures.offer;

class PreferenceLearnerTest {

    private static final OfferFeatures FEATURES = new OfferFeatures(new SkillDictionary());
    private final PreferenceLearner learner = new PreferenceLearner(FEATURES);

    private static JobOffer saved(String description) {
        return offer("Developer").description(description).mode(WorkMode.REMOTE).status(OfferStatus.SAVED).build();
    }

    private static Optional<LearnedPreferences.Weighted> weight(LearnedPreferences p, OfferFeatures.Type t, String key) {
        return p.weights().stream().filter(w -> w.feature().equals(new Feature(t, key))).findFirst();
    }

    @Test
    void belowThresholdLearningIsInactiveAndNeutral() {
        LearnedPreferences p = learner.learn(List.of(saved("Kafka"),
                offer("Python Dev").description("Python").rejected(RejectReason.TECH_STACK).build(),
                offer("Nowa").description("Kafka").build()));
        assertThat(p.active()).isFalse();
        assertThat(p.ratings()).isEqualTo(2);
        assertThat(p.adjust(saved("Kafka")).points()).isZero();
        assertThat(PreferencesView.of(p).note()).contains("brakuje 1 oceny");
    }

    @Test
    void likesAndDislikesFollowRatingsAndRejectReasons() {
        List<JobOffer> offers = List.of(
                saved("Java, Spring Boot, Kafka"),
                saved("Java, Kafka, PostgreSQL"),
                offer("Data").description("Python, Django, SQL").mode(WorkMode.ONSITE)
                        .rejected(RejectReason.TECH_STACK).build());
        LearnedPreferences p = learner.learn(offers);

        assertThat(p.active()).isTrue();
        assertThat(weight(p, OfferFeatures.Type.SKILL, "Kafka")).get()
                .satisfies(w -> assertThat(w.weight()).isPositive());
        assertThat(weight(p, OfferFeatures.Type.SKILL, "Python")).get()
                .satisfies(w -> assertThat(w.weight()).isNegative());
        // odrzucenie za technologie nie obniża trybu pracy
        assertThat(weight(p, OfferFeatures.Type.WORK_MODE, "ONSITE")).isEmpty();

        LearnedPreferences.Adjustment likes = p.adjust(saved("Kafka i Java"));
        assertThat(likes.points()).isPositive();
        assertThat(likes.reasons()).anyMatch(r -> r.matches("\\+\\d+: lubisz oferty z (Kafka|Java)"));
        LearnedPreferences.Adjustment dislikes = p.adjust(offer("Py").description("Python i Django").build());
        assertThat(dislikes.points()).isNegative();
        assertThat(dislikes.reasons()).anyMatch(r -> r.matches("−\\d+: zwykle odrzucasz oferty z (Python|Django)"));
    }

    @Test
    void workModeRejectionAffectsOnlyWorkMode() {
        List<JobOffer> offers = List.of(
                saved("Java"), saved("Java"),
                offer("Java onsite").description("Java").mode(WorkMode.ONSITE).rejected(RejectReason.WORK_MODE).build());
        LearnedPreferences p = learner.learn(offers);
        assertThat(weight(p, OfferFeatures.Type.WORK_MODE, "ONSITE")).get()
                .satisfies(w -> assertThat(w.weight()).isNegative());
        assertThat(weight(p, OfferFeatures.Type.SKILL, "Java")).get()
                .satisfies(w -> assertThat(w.negative()).isZero());
        assertThat(p.adjust(offer("x").mode(WorkMode.ONSITE).build()).reasons())
                .anyMatch(r -> r.contains("zwykle odrzucasz oferty stacjonarne"));
    }

    @Test
    void duplicateRejectionsAreIgnored() {
        LearnedPreferences p = learner.learn(List.of(saved("Kafka"), saved("Java"),
                offer("dup").description("Kafka").rejected(RejectReason.DUPLICATE).build()));
        assertThat(p.ratings()).isEqualTo(2);
        assertThat(p.active()).isFalse();
    }

    @Test
    void adjustmentIsCappedPerTypeAndInTotal() {
        String many = "Java, Kotlin, Spring Boot, Kafka, PostgreSQL, Docker, Kubernetes, AWS, Terraform, Redis";
        List<JobOffer> offers = List.of(
                offer("A").description(many).company("Firma X").contracts(ContractType.B2B).seniority(Seniority.SENIOR)
                        .mode(WorkMode.REMOTE).status(OfferStatus.SAVED).build(),
                offer("B").description(many).company("Firma X").contracts(ContractType.B2B).seniority(Seniority.SENIOR)
                        .mode(WorkMode.REMOTE).status(OfferStatus.SAVED).build(),
                offer("C").description("PHP, Laravel").company("Inna").contracts(ContractType.UOD)
                        .seniority(Seniority.JUNIOR).mode(WorkMode.ONSITE).rejected(RejectReason.OTHER).build());
        LearnedPreferences p = learner.learn(offers);
        JobOffer candidate = offer("D").description(many).company("Firma X sp. z o.o.").contracts(ContractType.B2B)
                .seniority(Seniority.SENIOR).mode(WorkMode.REMOTE).build();
        LearnedPreferences.Adjustment adj = p.adjust(candidate);
        assertThat(adj.points()).isEqualTo(RankingWeights.LEARNING_MAX_POINTS);
        assertThat(adj.reasons()).last().asString().contains("ograniczony");
        assertThat(adj.reasons()).anyMatch(r -> r.contains("z firmy Firma X"));
    }

    @Test
    void companyKeyIgnoresLegalForm() {
        assertThat(OfferFeatures.companyKey("LogiTech Solutions sp. z o.o.")).isEqualTo("logitech solutions");
        assertThat(OfferFeatures.companyKey("LogiTech Solutions")).isEqualTo("logitech solutions");
        assertThat(OfferFeatures.companyKey("Nordlys Software AB")).isEqualTo("nordlys software");
        assertThat(OfferFeatures.companyKey("BankPol S.A.")).isEqualTo("bankpol");
        assertThat(OfferFeatures.companyKey(null)).isEmpty();
    }

    @Test
    void phrasesAreReadablePolish() {
        assertThat(LearnedPreferences.phrase(OfferFeatures.Type.CONTRACT, "umowa o pracę")).isEqualTo("na umowę o pracę");
        assertThat(LearnedPreferences.phrase(OfferFeatures.Type.CONTRACT, "B2B")).isEqualTo("na B2B");
        assertThat(LearnedPreferences.phrase(OfferFeatures.Type.WORK_MODE, "hybrydowo")).isEqualTo("hybrydowe");
        assertThat(LearnedPreferences.phrase(OfferFeatures.Type.SENIORITY, "Senior")).isEqualTo("na poziomie Senior");
    }

    @Test
    void preferencesViewListsStrongestFirst() {
        List<JobOffer> offers = List.of(saved("Kafka, Kafka"), saved("Kafka, Java"),
                offer("Py").description("Python").rejected(RejectReason.TECH_STACK).build());
        PreferencesView v = PreferencesView.of(learner.learn(offers));
        assertThat(v.active()).isTrue();
        assertThat(v.minRatings()).isEqualTo(3);
        assertThat(v.features()).isNotEmpty();
        for (int i = 1; i < v.features().size(); i++) {
            assertThat(Math.abs(v.features().get(i - 1).weight())).isGreaterThanOrEqualTo(Math.abs(v.features().get(i).weight()));
        }
        assertThat(v.features()).anyMatch(f -> f.key().equals("Kafka") && f.explanation().contains("lubisz oferty z Kafka"));
    }
}

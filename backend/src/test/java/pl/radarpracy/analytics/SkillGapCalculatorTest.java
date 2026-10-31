package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.SkillGapCalculator.OfferMatch;
import pl.radarpracy.analytics.SkillGapCalculator.SkillGap;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillGapCalculatorTest {

    private final SkillDictionary dictionary = new SkillDictionary();
    private final MatchService matching = new MatchService(dictionary, null, null);
    private final CandidateProfile profile = new CandidateProfile();

    private static final String CV = """
            Java Developer — PayFlow (2022 – obecnie)
            - Mikroserwisy w Spring Boot i PostgreSQL.
            """;

    private OfferMatch offer(String title, Requirement... reqs) {
        JobOffer o = Fixtures.offer(title, "Firma " + title);
        o.setRequirements(List.of(reqs));
        return new OfferMatch(o, matching.matchRequirements(o.getRequirements(), CV, profile));
    }

    private List<SkillGap> compute(List<OfferMatch> offers) {
        return SkillGapCalculator.compute(offers,
                (m, skill) -> matching.matchRequirements(m.offer().getRequirements(), CV + "\n" + skill, profile).score(),
                skill -> dictionary.get(skill).map(Skill::category).orElse(null),
                skill -> dictionary.skillsIn(CV).contains(skill) || skill.equals("Java"));
    }

    @Test
    void countsMissingOffersMustHavesAndUnlockedOffersBySimulation() {
        List<OfferMatch> offers = List.of(
                // Java + Kubernetes obowiązkowe: wynik 50 → po Kubernetes 100 (odblokowana)
                offer("A", Requirement.must("Java"), Requirement.must("Kubernetes")),
                // Kubernetes obowiązkowy, Kafka obowiązkowa: 0 → po Kubernetes 50 (nadal poniżej 70)
                offer("B", Requirement.must("Kubernetes"), Requirement.must("Kafka")),
                // Kubernetes mile widziany: 3/4 = 75 → już powyżej progu
                offer("C", Requirement.must("Spring Boot"), Requirement.nice("Kubernetes")));
        assertThat(offers.get(0).result().score()).isEqualTo(50);
        assertThat(offers.get(1).result().score()).isZero();

        List<SkillGap> gaps = compute(offers);
        SkillGap k8s = gaps.get(0);
        assertThat(k8s.skill()).isEqualTo("Kubernetes");
        assertThat(k8s.offersMissing()).isEqualTo(3);
        assertThat(k8s.mustHaveMissing()).isEqualTo(2);
        assertThat(k8s.unlockedOffers()).isEqualTo(1);
        assertThat(k8s.unlocked().get(0).scoreBefore()).isEqualTo(50);
        assertThat(k8s.unlocked().get(0).scoreAfter()).isEqualTo(100);
        // zyski: 50 (A) + 50 (B) + 25 (C) → średnio 41,7
        assertThat(k8s.avgPointsLost()).isEqualTo(41.7);
        assertThat(k8s.category()).isNotBlank();
        assertThat(k8s.type()).isEqualTo(SkillGapCalculator.GapType.MISSING);
        assertThat(k8s.hint()).isNull();

        SkillGap kafka = gaps.stream().filter(g -> g.skill().equals("Kafka")).findFirst().orElseThrow();
        assertThat(kafka.offersMissing()).isEqualTo(1);
        assertThat(kafka.unlockedOffers()).isZero();
    }

    @Test
    void sortedByUnlockedThenMustHave() {
        List<OfferMatch> offers = List.of(
                offer("A", Requirement.must("Java"), Requirement.must("Kubernetes")),
                offer("B", Requirement.must("Java"), Requirement.nice("Go"), Requirement.nice("Rust")),
                offer("C", Requirement.nice("Rust"), Requirement.must("Spring Boot")));
        List<SkillGap> gaps = compute(offers);
        assertThat(gaps.get(0).skill()).isEqualTo("Kubernetes");
        assertThat(gaps).extracting(SkillGap::skill).contains("Go", "Rust");
    }

    @Test
    void softSkillsAndInsufficientLevelAreClassified() {
        List<SkillGap> gaps = compute(List.of(
                offer("D", Requirement.must("Komunikatywność"), Requirement.must("Spring Boot")),
                offer("E", new Requirement("Minimum 8 lat doświadczenia w Java", true, 8))));
        SkillGap soft = gaps.stream().filter(g -> g.skill().equals("Komunikatywność")).findFirst().orElseThrow();
        assertThat(soft.type()).isEqualTo(SkillGapCalculator.GapType.SOFT_SKILL);
        assertThat(soft.hint()).contains("przykładzie");
        SkillGap java = gaps.stream().filter(g -> g.skill().equals("Java")).findFirst().orElseThrow();
        assertThat(java.type()).isEqualTo(SkillGapCalculator.GapType.LEVEL);
        assertThat(java.avgPointsLost()).isZero(); // dopisanie „Java” nie doda lat doświadczenia
    }

    @Test
    void noOffersNoGaps() {
        assertThat(compute(List.of())).isEmpty();
    }
}

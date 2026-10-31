package pl.radarpracy.matching;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchServiceTest {

    private final MatchService service = new MatchService(new SkillDictionary(), null, null);

    private static final String CV = """
            Java Developer — PayFlow (2022 – obecnie)
            - Mikroserwisy w Spring Boot i PostgreSQL, Apache Kafka.
            Junior — SoftHouse (2021 – 2022): MySQL.
            Angielski — B2
            Politechnika Warszawska — Informatyka
            """;

    @Test
    void fullMatchHasVerifiedEvidenceQuote() {
        MatchResult r = service.matchRequirements(List.of(Requirement.must("Spring Boot"), Requirement.must("Kafka")),
                CV, new CandidateProfile());
        assertThat(r.score()).isEqualTo(100);
        RequirementMatch m = r.requirements().get(0);
        assertThat(m.level()).isEqualTo(MatchLevel.FULL);
        assertThat(m.evidenceVerified()).isTrue();
        assertThat(CV).contains(m.evidence());
        assertThat(m.evidence()).contains("Spring Boot");
    }

    @Test
    void impliedSkillCountsAsFull() {
        MatchResult r = service.matchRequirements(List.of(Requirement.must("Java"), Requirement.must("SQL")), CV,
                new CandidateProfile());
        assertThat(r.requirements()).allMatch(RequirementMatch::met);
    }

    @Test
    void relatedSkillGivesPartialCredit() {
        MatchResult r = service.matchRequirements(List.of(Requirement.must("Oracle DB")), CV, new CandidateProfile());
        RequirementMatch m = r.requirements().get(0);
        assertThat(m.level()).isEqualTo(MatchLevel.RELATED);
        assertThat(m.credit()).isEqualTo(0.4);
        assertThat(r.missingSkills()).contains("Oracle DB");
    }

    @Test
    void yearsAndLanguageLevelAreGraded() {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new SkillEntry("Java", 4, 4)));
        MatchResult r = service.matchRequirements(List.of(
                new Requirement("Minimum 8 lat doświadczenia w Java", true, 8),
                Requirement.must("Angielski C1")), CV, p);
        RequirementMatch years = r.requirements().get(0);
        assertThat(years.level()).isEqualTo(MatchLevel.PARTIAL);
        assertThat(years.candidateYears()).isEqualTo(4);
        assertThat(years.credit()).isEqualTo(0.5);
        RequirementMatch lang = r.requirements().get(1);
        assertThat(lang.level()).isEqualTo(MatchLevel.PARTIAL);
        assertThat(lang.note()).contains("C1").contains("B2");
    }

    @Test
    void profileOnlySkillSuggestsAddingToCv() {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new SkillEntry("Docker", 3, 2)));
        MatchResult r = service.matchRequirements(List.of(Requirement.must("Docker")), CV, p);
        assertThat(r.requirements().get(0).level()).isEqualTo(MatchLevel.PROFILE_ONLY);
        assertThat(r.requirements().get(0).note()).contains("dopisz");
    }

    @Test
    void anyOfRequirementAndWeightsAndExplanation() {
        MatchResult r = service.matchRequirements(List.of(
                Requirement.must("Kotlin lub Java"),
                Requirement.must("Kubernetes"),
                Requirement.nice("AWS")), CV, new CandidateProfile());
        assertThat(r.requirements().get(0).met()).isTrue();
        // wagi: 3 + 3 + 1 = 7; spełnione 3 → 43
        assertThat(r.score()).isEqualTo(43);
        assertThat(r.mustHaveMet()).isEqualTo(1);
        assertThat(r.explanation().get(0)).startsWith("−43 pkt: Kubernetes");
    }

    @Test
    void keywordFallbackForNonDictionaryRequirement() {
        MatchResult r = service.matchRequirements(List.of(Requirement.must("Doświadczenie w mikroserwisach płatniczych")),
                "Rozwijałam mikroserwisy płatnicze dla banków.", new CandidateProfile());
        assertThat(r.requirements().get(0).credit()).isGreaterThan(0);
    }
}

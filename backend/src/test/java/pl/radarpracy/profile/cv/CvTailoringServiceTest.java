package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.profile.cv.CvTailoringService.MissingAction;
import pl.radarpracy.profile.cv.CvTailoringService.MissingItem;
import pl.radarpracy.profile.cv.CvTailoringService.ProfileAddition;
import pl.radarpracy.profile.cv.CvTailoringService.TailoringResult;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvTailoringServiceTest {

    private final SkillDictionary dictionary = new SkillDictionary();
    private final MatchService matching = new MatchService(dictionary, null, null);
    private final CvTailoringService service = new CvTailoringService(matching, dictionary, null, null);

    private static final String CV_TEXT = """
            Anna Testowa
            DOŚWIADCZENIE
            - Wdrożyłam mikroserwisy w Java i Spring Boot, baza PostgreSQL.
            Angielski — B2
            """;

    private static CvVariant cv() {
        CvVariant cv = new CvVariant();
        cv.setId(7L);
        cv.setName("Backend");
        cv.setContent(CV_TEXT);
        return cv;
    }

    private static CandidateProfile profile() {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new SkillEntry("Docker", 3, 3), new SkillEntry("Terraform", 2, null),
                new SkillEntry("Java", 4, 4), new SkillEntry("aws", 2, 1)));
        return p;
    }

    private static JobOffer offer() {
        JobOffer o = new JobOffer();
        o.setTitle("Backend Developer");
        o.setRequirements(List.of(
                Requirement.must("Docker"),
                Requirement.must("Kubernetes"),
                Requirement.nice("Oracle DB"),
                Requirement.nice("Terraform"),
                Requirement.must("Java"),
                Requirement.must("Znajomość domeny bankowej"),
                Requirement.nice("Go lub Rust"),
                Requirement.nice("AWS lub Azure"),
                new Requirement("Min. 5 lat doświadczenia w Java", true, 5)));
        return o;
    }

    @Test
    void profileSkillsMissingInCvGetReadyLines() {
        TailoringResult r = service.tailor(offer(), cv(), profile());
        assertThat(r.cvId()).isEqualTo(7L);
        assertThat(r.fromProfile()).extracting(ProfileAddition::skill).containsExactly("Docker", "Terraform", "AWS");
        ProfileAddition docker = r.fromProfile().get(0);
        assertThat(docker.mustHave()).isTrue();
        assertThat(docker.profileYears()).isEqualTo(3);
        assertThat(docker.cvLine()).isEqualTo("Docker (3 lata)");
        // CV pisane w formie żeńskiej („Wdrożyłam”) — szkic zdania w tej samej formie, z miejscami do uzupełnienia
        assertThat(docker.sentence()).startsWith("Docker (3 lata doświadczenia): ").contains("wykorzystałam")
                .contains("tę technologię").contains("[");
        assertThat(docker.scoreGain()).isPositive();
        assertThat(r.fromProfile().get(1).cvLine()).isEqualTo("Terraform");
        assertThat(r.fromProfile().get(2).cvLine()).isEqualTo("AWS (1 rok)");
        assertThat(r.cvAddition()).isEqualTo("Docker (3 lata)\nTerraform\nAWS (1 rok)");
    }

    @Test
    void projectedScoreIsComputedOnCvWithAddedLines() {
        TailoringResult r = service.tailor(offer(), cv(), profile());
        int current = matching.match(offer(), cv(), profile()).score();
        assertThat(r.currentScore()).isEqualTo(current);
        int expected = matching.matchRequirements(offer().getRequirements(), CV_TEXT + "\n\n" + r.cvAddition(),
                profile()).score();
        assertThat(r.projectedScore()).isEqualTo(expected).isGreaterThan(current);
        assertThat(r.summary()).contains(current + "%").contains(expected + "%");
    }

    @Test
    void relatedAndMissingNeverSuggestAddingUnknownSkills() {
        TailoringResult r = service.tailor(offer(), cv(), profile());
        assertThat(r.related()).singleElement().satisfies(rel -> {
            assertThat(rel.skill()).isEqualTo("Oracle DB");
            assertThat(rel.relatedSkill()).isEqualTo("PostgreSQL");
            assertThat(CV_TEXT).contains(rel.evidence());
            assertThat(rel.evidence()).contains("PostgreSQL");
            assertThat(rel.advice()).contains("Nie wpisuj Oracle DB");
            assertThat(rel.sentence()).startsWith("PostgreSQL");
        });
        assertThat(r.missing()).extracting(MissingItem::skill).containsExactly("Kubernetes", null, "Go / Rust");
        MissingItem k8s = r.missing().get(0);
        assertThat(k8s.mustHave()).isTrue();
        assertThat(k8s.action()).isEqualTo(MissingAction.LEARN);
        assertThat(k8s.advice()).contains("Nie dopisuj");
        assertThat(r.missing().get(1).action()).isEqualTo(MissingAction.DESCRIBE_IF_TRUE);
        assertThat(r.missing().get(1).requirement()).isEqualTo("Znajomość domeny bankowej");
        assertThat(r.missing().get(2).action()).isEqualTo(MissingAction.SKIP);
        // propozycje do CV dotyczą wyłącznie umiejętności z profilu
        assertThat(r.cvAddition()).doesNotContain("Kubernetes", "Oracle", "Azure", "Go", "Rust");
        assertThat(r.fromProfile()).noneMatch(a -> a.sentence().contains("Kubernetes"));
    }

    @Test
    void partiallyMetRequirementsBecomeNotes() {
        TailoringResult r = service.tailor(offer(), cv(), profile());
        assertThat(r.notes()).singleElement().satisfies(n -> {
            assertThat(n.requirement()).isEqualTo("Min. 5 lat doświadczenia w Java");
            assertThat(n.note()).contains("5 lat").contains("4 lata");
        });
    }

    @Test
    void withoutCvOrRequirements() {
        TailoringResult none = service.tailor(offer(), null, profile());
        assertThat(none.cvId()).isNull();
        assertThat(none.fromProfile()).isEmpty();
        assertThat(none.summary()).contains("Dodaj CV");

        JobOffer empty = new JobOffer();
        empty.setTitle("Bez wymagań");
        TailoringResult r = service.tailor(empty, cv(), profile());
        assertThat(r.fromProfile()).isEmpty();
        assertThat(r.summary()).contains("nie ma wymagań");
    }

    @Test
    void languageAndSoftSkillTemplatesUsePlaceholders() {
        assertThat(service.cvLine("Niemiecki", new SkillEntry("Niemiecki", 3, 5))).contains("[wpisz poziom");
        assertThat(service.sentence("Komunikatywność", null, "m")).contains("[konkretna sytuacja");
        assertThat(service.sentence("Scrum", new SkillEntry("Scrum", 3, 2), "m")).contains("wykorzystałem tę metodykę");
        assertThat(service.sentence("Git", null, null)).contains("wykorzystałam/em to narzędzie");
    }

    @Test
    void genderDetectionFromVerbForms() {
        assertThat(CvTailoringService.genderOf("Wdrożyłam system i skróciłam czas.")).isEqualTo("f");
        assertThat(CvTailoringService.genderOf("Wdrożyłem system i zbudowałem API.")).isEqualTo("m");
        assertThat(CvTailoringService.genderOf("Rozwój systemu.")).isNull();
        assertThat(CvTailoringService.verb(null, "użył")).isEqualTo("użyłam/em");
    }
}

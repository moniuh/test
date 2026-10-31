package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvSkillExtractorTest {

    private final CvSkillExtractor extractor = new CvSkillExtractor(new SkillDictionary(), null);

    private static final String CV = """
            Anna Testowa
            PODSUMOWANIE
            Fullstack developerka: 4 lata z Java i Spring Boot, 2 lata z React i TypeScript.
            DOŚWIADCZENIE
            Backend Developer — Firma (2020 – 2023)
            - Panel w React 18, TypeScript i Redux (2 lata), testy w Jest.
            - Utrzymanie klastra Kubernetes
            Terraform — 3 lata
            UMIEJĘTNOŚCI
            Java, Spring Boot, PostgreSQL, Docker, Git
            JĘZYKI
            Angielski — B2 (5 lat nauki)
            """;

    private Map<String, CvSkillExtractor.SkillSuggestion> suggest(String cv, CandidateProfile profile) {
        return extractor.suggest(cv, profile).stream()
                .collect(Collectors.toMap(CvSkillExtractor.SkillSuggestion::name, Function.identity()));
    }

    @Test
    void recognizesSkillsWithVerbatimEvidence() {
        Map<String, CvSkillExtractor.SkillSuggestion> s = suggest(CV, new CandidateProfile());
        assertThat(s).containsKeys("Java", "Spring Boot", "React", "TypeScript", "Redux", "Jest", "Kubernetes",
                "Terraform", "PostgreSQL", "Docker", "Git", "Angielski");
        for (CvSkillExtractor.SkillSuggestion x : s.values()) {
            assertThat(x.evidence()).as(x.name()).isNotNull();
            assertThat(CV).as(x.name()).contains(x.evidence());
            if (x.yearsEvidence() != null) {
                assertThat(CV).contains(x.yearsEvidence());
            }
        }
        assertThat(s.get("Java").mentions()).isEqualTo(2);
        assertThat(s.get("Java").category()).isEqualTo("jezyk-programowania");
        assertThat(s.get("Kubernetes").evidence()).contains("Kubernetes");
    }

    @Test
    void yearsOnlyWhenAttributionIsReliable() {
        Map<String, CvSkillExtractor.SkillSuggestion> s = suggest(CV, new CandidateProfile());
        // fragment zdania z liczbą lat
        assertThat(s.get("Java").years()).isEqualTo(4);
        assertThat(s.get("Spring Boot").years()).isEqualTo(4);
        assertThat(s.get("React").years()).isEqualTo(2);
        assertThat(s.get("React").yearsEvidence()).isEqualTo("2 lata z React i TypeScript.");
        assertThat(s.get("Redux").years()).isEqualTo(2);
        assertThat(s.get("Terraform").years()).isEqualTo(3);
        // linia z kilkoma umiejętnościami, a lata w innym fragmencie — niewiarygodne
        assertThat(s.get("Jest").years()).isNull();
        assertThat(s.get("PostgreSQL").years()).isNull();
        // dla języków lata nie mają sensu („5 lat nauki” ≠ doświadczenie)
        assertThat(s.get("Angielski").years()).isNull();
    }

    @Test
    void singleSkillLineWithDateRangeAndPolishForms() {
        Map<String, CvSkillExtractor.SkillSuggestion> s = suggest("""
                Python Developer — DataCorp (2019 – 2022)
                Kotlin: 2,5 roku w projektach mobilnych
                Scala i Go — 5 lat
                Programista z 6 latami doświadczenia w C#
                """, new CandidateProfile());
        assertThat(s.get("Python").years()).isEqualTo(3);
        assertThat(s.get("Python").yearsEvidence()).isEqualTo("Python Developer — DataCorp (2019 – 2022)");
        assertThat(s.get("Kotlin").years()).isEqualTo(2);
        assertThat(s.get("Scala").years()).isEqualTo(5);
        assertThat(s.get("C#").years()).isEqualTo(6);
    }

    @Test
    void ambiguousLineGivesNoYears() {
        // lata są we fragmencie z Pythonem; Java jest w innym fragmencie linii z dwiema umiejętnościami
        Map<String, CvSkillExtractor.SkillSuggestion> s = suggest("Java, Python — 5 lat\n", new CandidateProfile());
        assertThat(s.get("Java").years()).isNull();
        assertThat(s.get("Python").years()).isEqualTo(5);
    }

    @Test
    void ambiguousAliasesNeedCapitalLetter() {
        Map<String, CvSkillExtractor.SkillSuggestion> s = suggest("""
                Ukończyłam studia w 2020 r. Mój kod jest testowany, lubię go refaktoryzować.
                Testy: Jest, Go, RStudio
                """, new CandidateProfile());
        // pojedyncza litera „R” jest zbyt niejednoznaczna — język R rozpoznają aliasy („RStudio”, „język R”)
        assertThat(s).containsKeys("Jest", "Go", "R");
        assertThat(s.get("Jest").mentions()).isEqualTo(1);
        assertThat(s.get("Go").mentions()).isEqualTo(1);
        assertThat(s.get("R").mentions()).isEqualTo(1);
        assertThat(suggest("CV jest krótkie, od 2019 r.", new CandidateProfile())).doesNotContainKeys("Jest", "R");
    }

    @Test
    void comparesWithProfileAndListsNewSkillsFirst() {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new SkillEntry("docker", 3, 3), new SkillEntry("Java", 4, 5)));
        List<CvSkillExtractor.SkillSuggestion> list = extractor.suggest(CV, p);
        Map<String, CvSkillExtractor.SkillSuggestion> s = list.stream()
                .collect(Collectors.toMap(CvSkillExtractor.SkillSuggestion::name, Function.identity()));
        assertThat(s.get("Docker").alreadyInProfile()).isTrue();
        assertThat(s.get("Java").alreadyInProfile()).isTrue();
        assertThat(s.get("Java").profileYears()).isEqualTo(5);
        assertThat(s.get("React").alreadyInProfile()).isFalse();
        int firstKnown = list.indexOf(list.stream().filter(CvSkillExtractor.SkillSuggestion::alreadyInProfile)
                .findFirst().orElseThrow());
        assertThat(list.subList(firstKnown, list.size())).allMatch(CvSkillExtractor.SkillSuggestion::alreadyInProfile);
        assertThat(list.get(0).name()).isEqualTo("Spring Boot");
    }

    @Test
    void emptyCvGivesNoSuggestions() {
        assertThat(extractor.suggest(null, null)).isEmpty();
        assertThat(extractor.suggest("Lubię spacery.", new CandidateProfile())).isEmpty();
    }

    @Test
    void clauseSeparatorsIgnoreDecimalsAndAbbreviations() {
        assertThat(CvSkillExtractor.isClauseSeparator("2,5 roku", 1)).isFalse();
        assertThat(CvSkillExtractor.isClauseSeparator("Java, SQL", 4)).isTrue();
        assertThat(CvSkillExtractor.isClauseSeparator("sp. z o.o.", 2)).isFalse();
        assertThat(CvSkillExtractor.isClauseSeparator("Koniec. Nowe zdanie", 6)).isTrue();
    }
}

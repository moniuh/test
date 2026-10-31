package pl.radarpracy.offer;

import org.junit.jupiter.api.Test;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementExtractorTest {

    private final RequirementExtractor extractor = new RequirementExtractor(new SkillDictionary());

    @Test
    void extractsMustAndNiceSections() {
        String text = """
                Senior Java Developer

                Wymagania:
                - Minimum 5 lat doświadczenia w Java
                - Kafka
                - Angielski B2

                Mile widziane:
                - AWS

                Oferujemy:
                - Opiekę medyczną
                """;
        List<Requirement> reqs = extractor.extract(text);
        assertThat(reqs).extracting(Requirement::text)
                .containsExactly("Minimum 5 lat doświadczenia w Java", "Kafka", "Angielski B2", "AWS");
        assertThat(reqs.get(0).mustHave()).isTrue();
        assertThat(reqs.get(0).minYears()).isEqualTo(5);
        assertThat(reqs.get(3).mustHave()).isFalse();
    }

    @Test
    void inlineNiceMarkerAndEnglishHeaders() {
        String text = """
                Requirements:
                - 3+ years with Spring Boot
                - Docker (nice to have)
                """;
        List<Requirement> reqs = extractor.extract(text);
        assertThat(reqs).hasSize(2);
        assertThat(reqs.get(0).minYears()).isEqualTo(3);
        assertThat(reqs.get(1).mustHave()).isFalse();
    }

    @Test
    void fallsBackToSkillsWithoutSections() {
        List<Requirement> reqs = extractor.extract("Szukamy osoby znającej Python i Django do pracy nad API.");
        assertThat(reqs).extracting(Requirement::text).containsExactly("Python", "Django");
    }

    @Test
    void yearsParser() {
        assertThat(YearsParser.requiredYears("co najmniej 2 lata doświadczenia")).isEqualTo(2);
        assertThat(YearsParser.requiredYears("3-5 years of experience")).isEqualTo(3);
        assertThat(YearsParser.requiredYears("Znajomość Javy")).isNull();
        assertThat(YearsParser.yearsInText("Java Developer (2021 – 2024)")).isEqualTo(3);
    }
}

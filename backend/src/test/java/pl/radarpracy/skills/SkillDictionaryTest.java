package pl.radarpracy.skills;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDictionaryTest {

    private final SkillDictionary dict = new SkillDictionary();

    @Test
    void recognizesSynonyms() {
        assertThat(dict.skillsIn("Znajomość k8s, Postgres i React.js")).containsExactly("Kubernetes", "PostgreSQL", "React");
        assertThat(dict.resolve("k8s")).map(Skill::name).contains("Kubernetes");
        assertThat(dict.canonicalName("springboot")).isEqualTo("Spring Boot");
    }

    @Test
    void longestAliasWinsAndBoundariesAreRespected() {
        assertThat(dict.skillsIn("React Native")).containsExactly("React Native");
        assertThat(dict.skillsIn("JavaScript")).containsExactly("JavaScript");
        assertThat(dict.skillsIn("C++ i C#")).containsExactly("C++", "C#");
        assertThat(dict.skillsIn("Node.js")).containsExactly("Node.js");
        assertThat(dict.skillsIn("postgresql")).doesNotContain("SQL");
    }

    @Test
    void polishInflectionViaPrefixAliases() {
        assertThat(dict.skillsIn("Znajomość języka angielskiego na poziomie B2")).contains("Angielski");
        assertThat(dict.skillsIn("Doświadczenie w księgowości")).contains("Księgowość");
    }

    @Test
    void impliesAndFamilies() {
        assertThat(dict.impliedBy("Spring Boot")).contains("Spring", "Java");
        assertThat(dict.sameFamily("MySQL", "PostgreSQL")).isTrue();
        assertThat(dict.sameFamily("Java", "Python")).isFalse();
    }

    @Test
    void hitIndicesPointToOriginalText() {
        String text = "Doświadczenie: Spring Boot, Żółć";
        SkillHit hit = dict.find(text).get(0);
        assertThat(text.substring(hit.start(), hit.end())).isEqualTo("Spring Boot");
    }
}

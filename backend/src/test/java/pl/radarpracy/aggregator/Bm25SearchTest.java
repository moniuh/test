package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25SearchTest {

    private static final List<Bm25Search.Doc> DOCS = List.of(
            new Bm25Search.Doc(1, "Java Developer", "Acme", "Java Spring Boot",
                    "Szukamy programisty. Java, Spring Boot, PostgreSQL. Praca hybrydowa w Krakowie."),
            new Bm25Search.Doc(2, "Fullstack Developer", "Beta", "React TypeScript Java",
                    "Frontend w React, backend okazjonalnie w Javie i Java EE."),
            new Bm25Search.Doc(3, "Księgowa", "Liczydło", "Excel",
                    "Pełna księgowość, Symfonia, Excel. Praca stacjonarna w Poznaniu."),
            new Bm25Search.Doc(4, "Analityk danych", "Gamma", "SQL Power BI",
                    "SQL i Power BI. Współpraca z działem księgowości przy raportach."),
            new Bm25Search.Doc(5, "Kierowca", "Szybko", "",
                    "Prawo jazdy kat. C. Mile widziana znajomość Javy? Nie, to żart."));

    private static List<Long> ids(String q) {
        return Bm25Search.search(DOCS, Bm25Search.parse(q)).stream().map(Bm25Search.Hit::id).toList();
    }

    @Test
    void titleMatchesRankAboveBodyMatches() {
        List<Long> java = ids("java");
        assertThat(java).startsWith(1L);
        assertThat(java).contains(2L);
        assertThat(java.indexOf(1L)).isLessThan(java.indexOf(2L));
    }

    @Test
    void polishCharactersAndStemmingFindInflectedForms() {
        // „ksiegowa” bez polskich znaków znajduje tytuł „Księgowa” (×3) wyżej niż „księgowości” w treści
        List<Long> r = ids("ksiegowa");
        assertThat(r).containsExactly(3L, 4L);
        assertThat(ids("Krakow")).containsExactly(1L);
    }

    @Test
    void phrasesAreRequiredAndExclusionsRemoveDocuments() {
        assertThat(ids("\"power bi\"")).containsExactly(4L);
        assertThat(ids("\"bi power\"")).isEmpty();
        assertThat(ids("java -react")).containsExactly(1L);
        assertThat(ids("developer -\"java ee\"")).containsExactly(1L);
        Bm25Search.Query q = Bm25Search.parse("java -React \"spring boot\"");
        assertThat(q.terms()).contains("java", "spring", "boot");
        assertThat(q.excludedTerms()).containsExactly("react");
        assertThat(q.phrases()).containsExactly("spring boot");
    }

    @Test
    void moreFrequentRareTermScoresHigherAndEmptyQueryReturnsNothing() {
        List<Bm25Search.Hit> hits = Bm25Search.search(DOCS, Bm25Search.parse("excel symfonia"));
        assertThat(hits).extracting(Bm25Search.Hit::id).containsExactly(3L);
        assertThat(hits.get(0).fields()).contains(Bm25Search.Field.SKILLS, Bm25Search.Field.BODY);
        assertThat(ids("")).isEmpty();
        assertThat(ids("i w z")).isEmpty();
    }

    @Test
    void snippetIsLiteralFragmentWithHighlights() {
        String body = "Oferujemy pracę w dużym zespole. ".repeat(10) + "Wymagamy znajomości PostgreSQL i Dockera. Reszta opisu.";
        Bm25Search.Snippet s = Bm25Search.snippet(body, Bm25Search.parse("postgresql"), 120);
        assertThat(s.snippet()).startsWith("…").contains("PostgreSQL");
        assertThat(body).contains(s.snippet().replace("…", ""));
        assertThat(s.highlights()).hasSize(1);
        int[] h = s.highlights().get(0);
        assertThat(s.snippet().substring(h[0], h[1])).isEqualTo("PostgreSQL");
    }
}

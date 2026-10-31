package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.radarpracy.matching.ranking.Fixtures.offer;

class TfIdfIndexTest {

    private static final SkillDictionary DICTIONARY = new SkillDictionary();

    private final JobOffer javaKafka = offer("Java Developer").id(1).company("FinPay")
            .description("Wymagania: Java 17, Spring Boot, Apache Kafka, PostgreSQL. Płatności natychmiastowe.").build();
    private final JobOffer javaSpring = offer("Backend Developer (Java)").id(2).company("Kredyto")
            .description("Wymagania: Java lub Kotlin, Spring Boot, PostgreSQL, Kafka mile widziana.").build();
    private final JobOffer frontend = offer("Frontend Developer").id(3).company("Sklepik")
            .description("Wymagania: React, TypeScript, Redux, CSS, Jest.").build();
    private final JobOffer python = offer("Junior Python Developer").id(4).company("DataWise")
            .description("Wymagania: Python, Django lub Flask, SQL, Pandas.").build();

    @Test
    void similarJavaOfferRanksAboveUnrelatedOnes() {
        List<JobOffer> all = List.of(javaKafka, javaSpring, frontend, python);
        List<SimilarOffer> similar = new TfIdfIndex(all, DICTIONARY).similarTo(javaKafka, all, 5);

        assertThat(similar).isNotEmpty();
        assertThat(similar.get(0).offerId()).isEqualTo(2L);
        assertThat(similar.get(0).sharedSkills()).contains("Java", "Spring Boot", "PostgreSQL", "Kafka");
        assertThat(similar).extracting(SimilarOffer::offerId).doesNotContain(1L);
        assertThat(similar).allMatch(s -> s.similarity() >= 0 && s.similarity() <= 1);
        for (int i = 1; i < similar.size(); i++) {
            assertThat(similar.get(i - 1).similarity()).isGreaterThanOrEqualTo(similar.get(i).similarity());
        }
    }

    @Test
    void identicalTextsAreFullySimilarAndSimilarityIsSymmetric() {
        JobOffer copy = offer("Java Developer").id(5).description(javaKafka.getDescription()).build();
        TfIdfIndex index = new TfIdfIndex(List.of(javaKafka, copy, frontend, python), DICTIONARY);
        assertThat(index.similarity(1L, 5L)).isCloseTo(1.0, within(1e-9));
        assertThat(index.similarity(1L, 3L)).isCloseTo(index.similarity(3L, 1L), within(1e-12));
        assertThat(index.similarity(1L, 999L)).isZero();
    }

    @Test
    void duplicatesInEitherDirectionAndSiblingsAreSkipped() {
        JobOffer original = offer("Java Developer").id(10).description("Java, Spring Boot, Kafka").build();
        JobOffer copy = offer("Java Developer").id(11).description("Java, Spring Boot, Kafka").duplicateOf(10L).build();
        JobOffer sibling = offer("Java Developer").id(12).description("Java, Spring Boot, Kafka").duplicateOf(10L).build();
        JobOffer other = offer("Java Engineer").id(13).description("Java, Spring Boot, PostgreSQL").build();
        List<JobOffer> all = List.of(original, copy, sibling, other);
        TfIdfIndex index = new TfIdfIndex(all, DICTIONARY);

        assertThat(index.similarTo(original, all, 5)).extracting(SimilarOffer::offerId).containsExactly(13L);
        assertThat(index.similarTo(copy, all, 5)).extracting(SimilarOffer::offerId).containsExactly(13L);
    }

    @Test
    void limitIsRespected() {
        List<JobOffer> all = List.of(javaKafka, javaSpring, frontend, python);
        assertThat(new TfIdfIndex(all, DICTIONARY).similarTo(javaKafka, all, 1)).hasSize(1);
    }
}

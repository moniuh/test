package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.skills.SkillDictionary;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.radarpracy.matching.ranking.Fixtures.SALARY;
import static pl.radarpracy.matching.ranking.Fixtures.TODAY;
import static pl.radarpracy.matching.ranking.Fixtures.match;
import static pl.radarpracy.matching.ranking.Fixtures.offer;
import static pl.radarpracy.matching.ranking.Fixtures.profile;

class RankingQueryTest {

    private static final LearnedPreferences NOT_LEARNED =
            new PreferenceLearner(new OfferFeatures(new SkillDictionary())).learn(List.of());

    private final JobOffer remoteHigh = offer("Senior Java Developer").id(1).company("FinPay").location("Kraków")
            .mode(WorkMode.REMOTE).contracts(ContractType.B2B).salary(25000, 30000).published(TODAY.minusDays(5))
            .description("Java, Kafka").build();
    private final JobOffer hybridMid = offer("Java Developer").id(2).company("Kredyto").location("Warszawa")
            .mode(WorkMode.HYBRID).contracts(ContractType.UOP).salary(16000, 20000).published(TODAY)
            .status(OfferStatus.SAVED).description("Java, Spring").build();
    private final JobOffer noSalary = offer("Python Developer").id(3).company("DataWise").location("Warszawa")
            .mode(WorkMode.REMOTE).published(TODAY.minusDays(1)).status(OfferStatus.ARCHIVED).description("Python")
            .build();
    private final JobOffer excluded = offer("Konsultant").id(4).company("Call").location("Warszawa")
            .mode(WorkMode.REMOTE).salary(18000, 22000).description("Praca zmianowa").build();

    private final RankingSnapshot snapshot = new RankingSnapshot(List.of(
            entry(remoteHigh, 90), entry(hybridMid, 70), entry(noSalary, 40), entry(excluded, 95)));

    private static RankingSnapshot.Entry entry(JobOffer o, int score) {
        return new RankingSnapshot.Entry(o, RankingScorer.score(o, match(o.getId(), score), SALARY.normalize(o),
                profile(), NOT_LEARNED, TODAY));
    }

    private static RankingQuery query(Set<OfferStatus> statuses, String q, boolean includeExcluded, Integer minScore,
                                      Set<WorkMode> modes, Integer minSalary, RankingSort sort) {
        return new RankingQuery(null, statuses, q, includeExcluded, minScore, modes, minSalary, sort);
    }

    private List<Long> ids(RankingQuery q) {
        return snapshot.select(q).stream().map(r -> r.offer().id()).toList();
    }

    @Test
    void defaultsHideExcludedAndSortByRank() {
        assertThat(ids(RankingQuery.defaults())).containsExactly(1L, 2L, 3L);
    }

    @Test
    void includeExcludedPutsExcludedLast() {
        assertThat(ids(query(null, null, true, null, null, null, RankingSort.MATCH))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void filtersByStatusScoreModeAndSalary() {
        assertThat(ids(query(Set.of(OfferStatus.NEW, OfferStatus.SAVED), null, false, null, null, null, null)))
                .containsExactly(1L, 2L);
        assertThat(ids(query(null, null, false, 60, null, null, null))).containsExactly(1L, 2L);
        assertThat(ids(query(null, null, false, null, Set.of(WorkMode.HYBRID), null, null))).containsExactly(2L);
        // górna granica 20 000 ≥ 20 000; oferta bez widełek odpada
        assertThat(ids(query(null, null, false, null, null, 20000, null))).containsExactly(1L, 2L);
        assertThat(ids(query(null, null, false, null, null, 21000, null))).containsExactly(1L);
    }

    @Test
    void textQueryFoldsDiacriticsAndRequiresEveryWord() {
        assertThat(ids(query(null, "krakow", false, null, null, null, null))).containsExactly(1L);
        assertThat(ids(query(null, "JAVA kafka", false, null, null, null, null))).containsExactly(1L);
        assertThat(ids(query(null, "java", false, null, null, null, null))).containsExactly(1L, 2L);
        assertThat(ids(query(null, "  ", false, null, null, null, null))).hasSize(3);
    }

    @Test
    void sortsByDateAndSalary() {
        assertThat(ids(query(null, null, false, null, null, null, RankingSort.DATE))).containsExactly(2L, 3L, 1L);
        assertThat(ids(query(null, null, false, null, null, null, RankingSort.SALARY))).containsExactly(1L, 2L, 3L);
    }

    @Test
    void rankTiesAreBrokenDeterministically() {
        JobOffer a = offer("A").id(21).mode(WorkMode.REMOTE).created(Instant.parse("2026-10-30T10:00:00Z")).build();
        JobOffer b = offer("A").id(20).mode(WorkMode.REMOTE).created(Instant.parse("2026-10-30T10:00:00Z")).build();
        RankingSnapshot s = new RankingSnapshot(List.of(entry(a, 50), entry(b, 50)));
        assertThat(s.select(RankingQuery.defaults())).extracting(r -> r.offer().id()).containsExactly(20L, 21L);
    }

    @Test
    void sortParsingIsCaseInsensitiveAndValidated() {
        assertThat(RankingSort.parse("salary")).isEqualTo(RankingSort.SALARY);
        assertThat(RankingSort.parse(null)).isEqualTo(RankingSort.RANK);
        assertThatThrownBy(() -> RankingSort.parse("cena")).isInstanceOf(BadRequestException.class);
    }
}

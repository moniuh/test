package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kryteria listy rankingu — wspólne dla {@code GET /api/ranking} i zapisanych wyszukiwań (#33).
 *
 * @param cvId            wersja CV (null = aktywna)
 * @param statuses        dozwolone statusy (puste = wszystkie)
 * @param q               fraza: każde słowo musi wystąpić w ogłoszeniu (porównanie po {@link Text#fold})
 * @param includeExcluded czy pokazywać oferty wykluczone przez deal-breakery
 * @param minScore        minimalne dopasowanie do CV (0–100) lub null
 * @param workModes       dozwolone tryby pracy (puste = wszystkie; oferty bez trybu odpadają, gdy ustawione)
 * @param minSalary       minimalna kwota (równoważnik UoP brutto); porównujemy górną granicę widełek
 *                        (a gdy jej brak — dolną); oferty bez widełek odpadają, gdy ustawione
 * @param sort            sortowanie
 */
public record RankingQuery(Long cvId, Set<OfferStatus> statuses, String q, boolean includeExcluded, Integer minScore,
                           Set<WorkMode> workModes, Integer minSalary, RankingSort sort) {

    public RankingQuery {
        statuses = withoutNulls(statuses);
        workModes = withoutNulls(workModes);
        q = Text.isBlank(q) ? null : q.strip();
        sort = sort == null ? RankingSort.RANK : sort;
    }

    /** Wszystkie oferty, bez wykluczonych, w kolejności rankingu. */
    public static RankingQuery defaults() {
        return new RankingQuery(null, null, null, false, null, null, null, RankingSort.RANK);
    }

    private static <T> Set<T> withoutNulls(Collection<T> values) {
        return values == null ? Set.of()
                : values.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    /** Czy oferta spełnia kryteria. */
    boolean matches(JobOffer offer, RankedOffer ranked) {
        if (!statuses.isEmpty() && !statuses.contains(offer.getStatus())) {
            return false;
        }
        if (!includeExcluded && ranked.excluded()) {
            return false;
        }
        if (minScore != null && ranked.matchScore() < minScore) {
            return false;
        }
        if (!workModes.isEmpty() && (offer.getWorkMode() == null || !workModes.contains(offer.getWorkMode()))) {
            return false;
        }
        if (minSalary != null && minSalary > 0
                && (ranked.comparableSalaryTop() == null || ranked.comparableSalaryTop() < minSalary)) {
            return false;
        }
        return q == null || matchesText(OfferTexts.searchable(offer), q);
    }

    /** Każde słowo frazy (po złożeniu) musi wystąpić w tekście. */
    static boolean matchesText(String foldedHaystack, String query) {
        List<String> terms = List.of(Text.fold(query).strip().split("\\s+"));
        return terms.stream().allMatch(foldedHaystack::contains);
    }
}

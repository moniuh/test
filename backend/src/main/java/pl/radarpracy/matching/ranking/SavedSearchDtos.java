package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;

import java.time.Instant;
import java.util.List;

/** Obiekty REST zapisanych wyszukiwań (pomysł #33). */
public final class SavedSearchDtos {

    private SavedSearchDtos() {
    }

    /** Dane do utworzenia lub zmiany zapisanego wyszukiwania. */
    public record SavedSearchRequest(String name, String query, Integer minScore, Integer minSalary,
                                     List<WorkMode> workModes, List<OfferStatus> statuses, Boolean includeExcluded,
                                     Long cvId) {
    }

    /**
     * Zapisane wyszukiwanie z licznikami.
     *
     * @param totalCount liczba ofert spełniających kryteria
     * @param newCount   liczba ofert spełniających kryteria dodanych po {@code lastCheckedAt}
     */
    public record SavedSearchView(Long id, String name, String query, Integer minScore, Integer minSalary,
                                  List<WorkMode> workModes, List<OfferStatus> statuses, boolean includeExcluded,
                                  Long cvId, Instant lastCheckedAt, Instant createdAt, int totalCount, int newCount) {
    }

    /**
     * Wyniki zapisanego wyszukiwania.
     *
     * @param search      wyszukiwanie z licznikami
     * @param items       oferty w kolejności rankingu
     * @param newOfferIds id ofert dodanych po ostatnim przejrzeniu
     */
    public record SavedSearchResults(SavedSearchView search, List<RankedOffer> items, List<Long> newOfferIds) {
    }
}

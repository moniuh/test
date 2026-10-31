package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.JobOffer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ranking wszystkich ofert policzony raz — można go wielokrotnie filtrować (np. dla każdego
 * zapisanego wyszukiwania) bez ponownego liczenia dopasowania.
 */
public final class RankingSnapshot {

    /** Oferta i jej pozycja w rankingu. */
    record Entry(JobOffer offer, RankedOffer ranked) {
    }

    private final List<Entry> entries;
    private final Map<Long, RankedOffer> byOfferId;

    RankingSnapshot(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.byOfferId = this.entries.stream()
                .collect(Collectors.toMap(e -> e.offer().getId(), Entry::ranked, (a, b) -> a));
    }

    /** Pozycja rankingu jednej oferty (np. dla skrzynki agregatora). */
    public java.util.Optional<RankedOffer> get(Long offerId) {
        return java.util.Optional.ofNullable(byOfferId.get(offerId));
    }

    /** Oferty spełniające kryteria, posortowane wg {@link RankingQuery#sort()}. */
    public List<RankedOffer> select(RankingQuery query) {
        return entries.stream()
                .filter(e -> query.matches(e.offer(), e.ranked()))
                .map(Entry::ranked)
                .sorted(query.sort().comparator())
                .toList();
    }

    public int size() {
        return entries.size();
    }
}

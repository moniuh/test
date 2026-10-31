package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.JobOffer;

import java.util.List;

/**
 * Ranking wszystkich ofert policzony raz — można go wielokrotnie filtrować (np. dla każdego
 * zapisanego wyszukiwania) bez ponownego liczenia dopasowania.
 */
public final class RankingSnapshot {

    /** Oferta i jej pozycja w rankingu. */
    record Entry(JobOffer offer, RankedOffer ranked) {
    }

    private final List<Entry> entries;

    RankingSnapshot(List<Entry> entries) {
        this.entries = List.copyOf(entries);
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

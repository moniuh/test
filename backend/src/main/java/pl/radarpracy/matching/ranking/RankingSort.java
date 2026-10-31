package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.BadRequestException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Locale;

/**
 * Sortowanie listy rankingu. Oferty wykluczone zawsze trafiają na koniec; remisy rozstrzyga
 * ranking, potem dopasowanie, a na końcu id oferty — kolejność jest deterministyczna.
 */
public enum RankingSort {
    /** ranking (dopasowanie × preferencje) */
    RANK,
    /** samo dopasowanie do CV */
    MATCH,
    /** najnowsze (data publikacji, a gdy jej brak — dodania) */
    DATE,
    /** wynagrodzenie (środek widełek jako równoważnik UoP brutto); oferty bez widełek na końcu */
    SALARY;

    public static RankingSort parse(String value) {
        if (value == null || value.isBlank()) {
            return RANK;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Nieznane sortowanie: " + value + " (dozwolone: rank, match, date, salary)");
        }
    }

    Comparator<RankedOffer> comparator() {
        Comparator<RankedOffer> tie = Comparator.comparingInt((RankedOffer r) -> -r.rankScore())
                .thenComparingInt(r -> -r.matchScore())
                .thenComparing(r -> r.offer().id(), Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<RankedOffer> primary = switch (this) {
            case RANK -> Comparator.comparingInt(r -> -r.rankScore());
            case MATCH -> Comparator.comparingInt(r -> -r.matchScore());
            case DATE -> Comparator.comparing(RankingSort::date, Comparator.nullsLast(Comparator.reverseOrder()));
            case SALARY -> Comparator.comparing(RankedOffer::comparableSalaryMid,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
        return Comparator.comparing(RankedOffer::excluded).thenComparing(primary).thenComparing(tie);
    }

    private static LocalDate date(RankedOffer r) {
        if (r.offer().publishedAt() != null) {
            return r.offer().publishedAt();
        }
        return r.offer().createdAt() == null ? null : LocalDate.ofInstant(r.offer().createdAt(), ZoneId.systemDefault());
    }
}

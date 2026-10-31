package pl.radarpracy.aggregator;

import java.util.List;

/**
 * Filtry na poziomie źródła (pomysł #60), stosowane przed zapisem oferty.
 *
 * @param includeKeywords co najmniej jedno słowo/fraza musi wystąpić (pusta lista = bez warunku)
 * @param excludeKeywords żadne słowo/fraza nie może wystąpić
 * @param locations       lokalizacja oferty musi zawierać jedną z podanych (albo „zdalnie” dla pracy zdalnej)
 * @param minSalary       minimalne wynagrodzenie miesięczne w równoważniku UoP brutto (PLN)
 */
public record SourceFilters(List<String> includeKeywords, List<String> excludeKeywords, List<String> locations,
                            Integer minSalary) {

    public SourceFilters {
        includeKeywords = clean(includeKeywords);
        excludeKeywords = clean(excludeKeywords);
        locations = clean(locations);
        minSalary = minSalary == null || minSalary <= 0 ? null : minSalary;
    }

    public static SourceFilters none() {
        return new SourceFilters(List.of(), List.of(), List.of(), null);
    }

    public boolean isEmpty() {
        return includeKeywords.isEmpty() && excludeKeywords.isEmpty() && locations.isEmpty() && minSalary == null;
    }

    private static List<String> clean(List<String> in) {
        if (in == null) {
            return List.of();
        }
        return in.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).distinct().toList();
    }
}

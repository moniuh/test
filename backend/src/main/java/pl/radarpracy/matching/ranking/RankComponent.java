package pl.radarpracy.matching.ranking;

/**
 * Składnik rankingu oferty (pomysł #31).
 *
 * @param key         identyfikator składnika (np. „cv”, „salary”)
 * @param name        nazwa dla kandydata
 * @param value       ocena składnika 0–1 (dla korekty z uczenia: −1…+1)
 * @param weight      waga z {@link RankingWeights.Factor}
 * @param points      wkład w ranking w punktach ({@code value × weight × 100})
 * @param neutral     true, gdy brakuje danych i przyjęto wartość neutralną
 * @param explanation wyjaśnienie po polsku
 */
public record RankComponent(String key, String name, double value, double weight, double points, boolean neutral,
                            String explanation) {

    static RankComponent of(RankingWeights.Factor factor, double value, boolean neutral, String explanation) {
        return new RankComponent(factor.key(), factor.label(), round(value, 3), factor.weight(),
                round(rawPoints(factor, value), 1), neutral, explanation);
    }

    static RankComponent neutral(RankingWeights.Factor factor, String explanation) {
        return of(factor, RankingWeights.NEUTRAL, true, explanation);
    }

    static double rawPoints(RankingWeights.Factor factor, double value) {
        return value * factor.weight() * 100;
    }

    static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}

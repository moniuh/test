package pl.radarpracy.matching.ranking;

import pl.radarpracy.matching.ranking.OfferFeatures.Feature;
import pl.radarpracy.offer.JobOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wyuczone wagi cech (pomysł #35) i korekta rankingu z nich wynikająca.
 *
 * <p>Waga cechy to wygładzony log-odds: jak często cecha występuje w ofertach zapisanych w porównaniu
 * z odrzuconymi (z uwzględnieniem powodu odrzucenia). Korekta oferty to suma wkładów jej cech
 * ({@code waga × }{@link RankingWeights#LEARNING_POINTS_PER_LOG_ODDS}), z limitami na rodzaj cechy
 * i łącznym limitem ±{@link RankingWeights#LEARNING_MAX_POINTS} pkt.</p>
 */
final class LearnedPreferences {

    /**
     * Cecha z wagą.
     *
     * @param feature  cecha
     * @param label    etykieta do wyświetlenia
     * @param weight   wygładzony log-odds
     * @param positive w ilu zapisanych ofertach wystąpiła
     * @param negative w ilu odrzuconych ofertach (z pasującym powodem) wystąpiła
     */
    record Weighted(Feature feature, String label, double weight, int positive, int negative) {

        double points() {
            return weight * RankingWeights.LEARNING_POINTS_PER_LOG_ODDS;
        }

        /** „+4: lubisz oferty z Kafka” / „−3: zwykle odrzucasz oferty stacjonarne”. */
        String explanation() {
            long p = Math.round(points());
            String what = phrase(feature.type(), label);
            return RankingLabels.signedPoints(p) + ": " + (p >= 0 ? "lubisz oferty " : "zwykle odrzucasz oferty ") + what;
        }
    }

    /**
     * Korekta rankingu jednej oferty.
     *
     * @param points  wkład w punktach (−10…+10)
     * @param reasons najważniejsze cechy, które zadecydowały
     */
    record Adjustment(double points, List<String> reasons) {
        static final Adjustment NONE = new Adjustment(0, List.of());
    }

    private static final int MAX_REASONS = 5;

    private final boolean active;
    private final int positiveCount;
    private final int negativeCount;
    private final Map<Feature, Weighted> weights;
    private final OfferFeatures features;

    LearnedPreferences(boolean active, int positiveCount, int negativeCount, Map<Feature, Weighted> weights,
                       OfferFeatures features) {
        this.active = active;
        this.positiveCount = positiveCount;
        this.negativeCount = negativeCount;
        this.weights = weights;
        this.features = features;
    }

    boolean active() {
        return active;
    }

    int positiveCount() {
        return positiveCount;
    }

    int negativeCount() {
        return negativeCount;
    }

    int ratings() {
        return positiveCount + negativeCount;
    }

    /** Cechy z niezerową wagą, od najsilniejszych. */
    List<Weighted> weights() {
        return weights.values().stream()
                .filter(w -> Math.abs(w.weight()) > 1e-9)
                .sorted(Comparator.comparingDouble((Weighted w) -> -Math.abs(w.weight()))
                        .thenComparing(w -> w.feature().type())
                        .thenComparing(w -> w.feature().key()))
                .toList();
    }

    Adjustment adjust(JobOffer offer) {
        if (!active) {
            return Adjustment.NONE;
        }
        Map<OfferFeatures.Type, List<Weighted>> present = new EnumMap<>(OfferFeatures.Type.class);
        for (Feature f : features.of(offer).keySet()) {
            Weighted w = weights.get(f);
            if (w != null && Math.abs(w.weight()) > 1e-9) {
                present.computeIfAbsent(f.type(), t -> new ArrayList<>()).add(w);
            }
        }
        Map<OfferFeatures.Type, Double> byType = new EnumMap<>(OfferFeatures.Type.class);
        double total = 0;
        boolean capped = false;
        for (Map.Entry<OfferFeatures.Type, List<Weighted>> e : present.entrySet()) {
            double cap = e.getKey().cap();
            double v = e.getValue().stream().mapToDouble(Weighted::points).sum();
            if (Math.abs(v) > cap) {
                capped = true;
                v = Math.signum(v) * cap;
            }
            byType.put(e.getKey(), v);
            total += v;
        }
        double max = RankingWeights.LEARNING_MAX_POINTS;
        if (Math.abs(total) > max) {
            capped = true;
            total = Math.signum(total) * max;
        }
        // wyjaśnienie: rodzaje cech od największego wpływu; z umiejętności dwie najsilniejsze, z innych jedna
        Comparator<Weighted> strongest = Comparator.comparingDouble((Weighted w) -> -Math.abs(w.points()))
                .thenComparing(w -> w.feature().key());
        List<String> reasons = new ArrayList<>();
        byType.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<OfferFeatures.Type, Double> e) -> -Math.abs(e.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> present.get(e.getKey()).stream()
                        .filter(w -> Math.round(w.points()) != 0)
                        .sorted(strongest)
                        .limit(e.getKey() == OfferFeatures.Type.SKILL ? 2 : 1)
                        .forEach(w -> reasons.add(w.explanation())));
        while (reasons.size() > MAX_REASONS) {
            reasons.remove(reasons.size() - 1);
        }
        if (capped && !reasons.isEmpty()) {
            reasons.add(String.format(Locale.ROOT, "wpływ ograniczony do %s pkt", RankingLabels.signedPoints(Math.round(total))));
        }
        return new Adjustment(total, reasons);
    }

    /** Opis cechy w zdaniu „lubisz oferty …”. */
    static String phrase(OfferFeatures.Type type, String label) {
        return switch (type) {
            case SKILL -> "z " + label;
            case WORK_MODE -> switch (label) {
                case "zdalnie" -> "zdalne";
                case "hybrydowo" -> "hybrydowe";
                default -> "stacjonarne";
            };
            case CONTRACT -> "na " + (label.startsWith("umowa") ? "umowę" + label.substring("umowa".length()) : label);
            case SENIORITY -> "na poziomie " + label;
            case COMPANY -> "z firmy " + label;
        };
    }
}

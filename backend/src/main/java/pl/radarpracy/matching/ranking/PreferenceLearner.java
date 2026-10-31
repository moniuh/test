package pl.radarpracy.matching.ranking;

import pl.radarpracy.matching.ranking.OfferFeatures.Feature;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uczenie preferencji z ocen kandydata (pomysł #35).
 *
 * <p>Pozytywne oceny to oferty zapisane (SAVED), negatywne — odrzucone (REJECTED). Powód odrzucenia
 * zawęża sygnał: odrzucenie za technologie obniża tylko umiejętności, za tryb pracy — tylko tryb itd.;
 * odrzucenie jako duplikat jest pomijane. Dla cechy {@code f} liczymy wygładzony log-odds
 * (Laplace, α = {@link RankingWeights#LEARNING_SMOOTHING}):</p>
 *
 * <pre>w(f) = ln((pos_f + α) / (P + 2α)) − ln((neg_f + α) / (N_t + 2α))</pre>
 *
 * <p>gdzie P to liczba ofert zapisanych, a N_t — odrzuconych z powodem dotyczącym rodzaju cechy.
 * Waga dodatnia wymaga choć jednego wystąpienia w ofertach zapisanych, ujemna — w odrzuconych
 * (bez tego wagę zerujemy, żeby nie „uczyć się” z samego braku danych). Poniżej
 * {@link RankingWeights#LEARNING_MIN_RATINGS} ocen uczenie jest wyłączone (wpływ neutralny).</p>
 */
final class PreferenceLearner {

    private final OfferFeatures features;

    PreferenceLearner(OfferFeatures features) {
        this.features = features;
    }

    LearnedPreferences learn(List<JobOffer> offers) {
        double a = RankingWeights.LEARNING_SMOOTHING;
        int positives = 0;
        int negatives = 0;
        Map<OfferFeatures.Type, Integer> negativesByType = new EnumMap<>(OfferFeatures.Type.class);
        Map<Feature, int[]> counts = new LinkedHashMap<>();
        Map<Feature, String> labels = new LinkedHashMap<>();

        for (JobOffer o : offers) {
            boolean positive = o.getStatus() == OfferStatus.SAVED;
            boolean negative = o.getStatus() == OfferStatus.REJECTED && o.getRejectReason() != RejectReason.DUPLICATE;
            if (!positive && !negative) {
                continue;
            }
            if (positive) {
                positives++;
            } else {
                negatives++;
                for (OfferFeatures.Type t : OfferFeatures.Type.values()) {
                    if (t.affectedBy(o.getRejectReason())) {
                        negativesByType.merge(t, 1, Integer::sum);
                    }
                }
            }
            for (Map.Entry<Feature, String> e : features.of(o).entrySet()) {
                Feature f = e.getKey();
                if (negative && !f.type().affectedBy(o.getRejectReason())) {
                    continue;
                }
                labels.putIfAbsent(f, e.getValue());
                counts.computeIfAbsent(f, k -> new int[2])[positive ? 0 : 1]++;
            }
        }

        Map<Feature, LearnedPreferences.Weighted> weights = new LinkedHashMap<>();
        for (Map.Entry<Feature, int[]> e : counts.entrySet()) {
            Feature f = e.getKey();
            int pos = e.getValue()[0];
            int neg = e.getValue()[1];
            int nType = negativesByType.getOrDefault(f.type(), 0);
            double w = Math.log((pos + a) / (positives + 2 * a)) - Math.log((neg + a) / (nType + 2 * a));
            if (neg == 0) {
                w = Math.max(0, w);
            }
            if (pos == 0) {
                w = Math.min(0, w);
            }
            weights.put(f, new LearnedPreferences.Weighted(f, labels.get(f), w, pos, neg));
        }
        boolean active = positives + negatives >= RankingWeights.LEARNING_MIN_RATINGS;
        return new LearnedPreferences(active, positives, negatives, weights, features);
    }
}

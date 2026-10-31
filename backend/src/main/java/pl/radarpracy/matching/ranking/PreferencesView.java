package pl.radarpracy.matching.ranking;

import java.util.List;

/**
 * Podgląd wyuczonych preferencji (pomysł #35) — {@code GET /api/ranking/preferences}.
 *
 * @param active        czy uczenie wpływa na ranking
 * @param ratings       liczba ocen (zapisane + odrzucone, bez duplikatów)
 * @param positive      liczba ofert zapisanych
 * @param negative      liczba ofert odrzuconych
 * @param minRatings    próg włączenia uczenia
 * @param maxPoints     maksymalny wpływ na ranking (±pkt)
 * @param features      cechy z wagami, od najsilniejszych
 * @param note          wyjaśnienie stanu dla kandydata
 */
public record PreferencesView(boolean active, int ratings, int positive, int negative, int minRatings,
                              double maxPoints, List<FeatureWeight> features, String note) {

    /**
     * Wyuczona waga cechy.
     *
     * @param type        rodzaj: SKILL, WORK_MODE, CONTRACT, SENIORITY, COMPANY
     * @param key         klucz cechy
     * @param label       etykieta
     * @param weight      wygładzony log-odds
     * @param points      wkład w ranking oferty z tą cechą (przed limitami)
     * @param positive    wystąpienia w ofertach zapisanych
     * @param negative    wystąpienia w ofertach odrzuconych
     * @param explanation np. „+4: lubisz oferty z Kafka”
     */
    public record FeatureWeight(String type, String key, String label, double weight, double points, int positive,
                                int negative, String explanation) {
    }

    static PreferencesView of(LearnedPreferences learned) {
        List<FeatureWeight> features = learned.weights().stream()
                .map(w -> new FeatureWeight(w.feature().type().name(), w.feature().key(), w.label(),
                        RankComponent.round(w.weight(), 3), RankComponent.round(w.points(), 1), w.positive(),
                        w.negative(), w.explanation()))
                .toList();
        int min = RankingWeights.LEARNING_MIN_RATINGS;
        String note;
        if (learned.active()) {
            note = "Uczenie aktywne: " + learned.ratings() + " " + RankingLabels.plural(learned.ratings(), "ocena", "oceny", "ocen")
                    + " (" + learned.positiveCount() + " zapisanych, " + learned.negativeCount()
                    + " odrzuconych). Wpływ na ranking najwyżej ±" + Math.round(RankingWeights.LEARNING_MAX_POINTS) + " pkt.";
        } else {
            int missing = min - learned.ratings();
            note = "Uczenie włączy się po " + min + " ocenach — brakuje " + missing + " "
                    + RankingLabels.plural(missing, "oceny", "ocen", "ocen")
                    + ". Zapisuj oferty, które Ci się podobają, i odrzucaj pozostałe z podaniem powodu.";
        }
        return new PreferencesView(learned.active(), learned.ratings(), learned.positiveCount(),
                learned.negativeCount(), min, RankingWeights.LEARNING_MAX_POINTS, features, note);
    }
}

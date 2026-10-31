package pl.radarpracy.offer.analysis;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Locale;

/**
 * Ocena ryzyka, że ogłoszenie jest oszustwem (#19). Wynik punktowy liczony w kodzie (suma punktów
 * sygnałów, maks. 100) i przełożony na poziom według jawnych progów.
 *
 * @param level           low / medium / high
 * @param levelLabel      „niskie” / „średnie” / „wysokie”
 * @param score           0–100
 * @param mediumThreshold od tylu punktów ryzyko jest średnie
 * @param highThreshold   od tylu punktów ryzyko jest wysokie
 * @param signals         wykryte sygnały z cytatami
 * @param recommendations zalecenia dla kandydata
 * @param method          opis metody (przejrzystość)
 */
public record ScamAssessment(RiskLevel level, String levelLabel, int score, int mediumThreshold, int highThreshold,
                             List<Signal> signals, List<String> recommendations, String method) {

    public enum RiskLevel {
        LOW("niskie"), MEDIUM("średnie"), HIGH("wysokie");

        private final String label;

        RiskLevel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Sygnał oszustwa.
     *
     * @param code   kod reguły
     * @param title  opis po polsku
     * @param points punkty doliczone do wyniku
     * @param why    dlaczego to sygnał
     * @param quote  dosłowny fragment ogłoszenia (null, gdy sygnał dotyczy braku czegoś)
     */
    public record Signal(String code, String title, int points, String why, String quote) {
    }
}

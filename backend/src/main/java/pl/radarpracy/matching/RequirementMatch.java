package pl.radarpracy.matching;

import java.util.List;

/**
 * Wynik dopasowania jednego wymagania.
 *
 * @param requirement      treść wymagania
 * @param mustHave         czy obowiązkowe
 * @param weight           waga w wyniku (obowiązkowe 3, mile widziane 1)
 * @param level            stopień spełnienia
 * @param credit           0–1, jaka część wagi została zaliczona
 * @param points           punkty zdobyte w skali 0–100 całego wyniku
 * @param maxPoints        punkty możliwe do zdobycia za to wymaganie
 * @param skills           umiejętności rozpoznane w wymaganiu
 * @param evidence         cytat z CV (dosłowny fragment) lub null
 * @param evidenceVerified czy cytat jest podciągiem CV (zawsze sprawdzane w kodzie)
 * @param note             wyjaśnienie dla kandydata
 * @param minYears         wymagane lata, jeśli podane
 * @param candidateYears   lata kandydata, jeśli znane
 */
public record RequirementMatch(String requirement, boolean mustHave, int weight, MatchLevel level, double credit,
                               double points, double maxPoints, List<String> skills, String evidence,
                               boolean evidenceVerified, String note, Integer minYears, Integer candidateYears) {

    /** Czy wymaganie uznajemy za spełnione (≥ 75% wagi). */
    public boolean met() {
        return credit >= 0.75;
    }
}

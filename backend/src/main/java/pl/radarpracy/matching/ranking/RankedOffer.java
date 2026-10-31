package pl.radarpracy.matching.ranking;

import java.util.List;

/**
 * Oferta w rankingu (pomysły #31, #32, #35).
 *
 * @param offer                podstawowe pola oferty
 * @param matchScore           dopasowanie do CV 0–100 (z {@code MatchService})
 * @param matchSummary         jedno zdanie podsumowania dopasowania
 * @param missingSkills        umiejętności z wymagań, których brakuje w CV
 * @param rankScore            ranking 0–100: suma punktów składników, obcięta do 0–100
 * @param comparableSalaryMin  dolna granica widełek jako równoważnik UoP brutto (lub null)
 * @param comparableSalaryMax  górna granica widełek jako równoważnik UoP brutto (lub null)
 * @param comparableSalaryMid  środek widełek jako równoważnik UoP brutto (lub null)
 * @param components           rozbicie rankingu na składniki
 * @param excluded             czy oferta jest wykluczona przez deal-breaker
 * @param exclusions           powody wykluczenia (pusta lista, gdy nie jest wykluczona)
 */
public record RankedOffer(OfferBrief offer, int matchScore, String matchSummary, List<String> missingSkills,
                          int rankScore, Integer comparableSalaryMin, Integer comparableSalaryMax,
                          Integer comparableSalaryMid, List<RankComponent> components, boolean excluded,
                          List<Exclusion> exclusions) {

    /** Najlepsza znana kwota z widełek (górna granica, a gdy jej brak — dolna); używa jej filtr „min. wynagrodzenie”. */
    public Integer comparableSalaryTop() {
        return comparableSalaryMax != null ? comparableSalaryMax : comparableSalaryMin;
    }
}

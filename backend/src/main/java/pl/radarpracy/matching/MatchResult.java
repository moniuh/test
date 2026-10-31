package pl.radarpracy.matching;

import java.util.List;

/**
 * Pełny wynik dopasowania oferty do wersji CV.
 *
 * @param offerId        id oferty
 * @param cvId           id wersji CV (null, gdy brak CV)
 * @param cvName         nazwa wersji CV
 * @param score          wynik 0–100 (liczony deterministycznie w kodzie)
 * @param mustHaveMet    spełnione wymagania obowiązkowe
 * @param mustHaveTotal  wszystkie wymagania obowiązkowe
 * @param niceMet        spełnione mile widziane
 * @param niceTotal      wszystkie mile widziane
 * @param requirements   szczegóły dla każdego wymagania
 * @param matchedSkills  umiejętności z wymagań, które kandydat ma
 * @param missingSkills  umiejętności z wymagań, których brakuje (także te z pokrewnym zamiennikiem)
 * @param summary        jedno zdanie podsumowania
 * @param explanation    „dlaczego taki wynik” — największe straty i zyski punktów (pomysł #34)
 */
public record MatchResult(Long offerId, Long cvId, String cvName, int score, int mustHaveMet, int mustHaveTotal,
                          int niceMet, int niceTotal, List<RequirementMatch> requirements, List<String> matchedSkills,
                          List<String> missingSkills, String summary, List<String> explanation) {
}

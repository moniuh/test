package pl.radarpracy.matching;

import java.util.List;

/** Skrócony wynik dopasowania do list i rankingu. */
public record MatchSummary(Long offerId, int score, int mustHaveMet, int mustHaveTotal, List<String> missingSkills) {

    public static MatchSummary of(MatchResult r) {
        return new MatchSummary(r.offerId(), r.score(), r.mustHaveMet(), r.mustHaveTotal(), r.missingSkills());
    }
}

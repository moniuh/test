package pl.radarpracy.tailoring;

import pl.radarpracy.matching.MatchLevel;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.RequirementMatch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wynik dopasowania przed i po tailoringu (pomysł #78) — oba liczone tym samym kodem
 * ({@code MatchService}), wymaganie po wymaganiu.
 *
 * @param before        wynik CV bazowego
 * @param after         wynik wersji dopasowanej
 * @param delta         różnica
 * @param requirements  zmiany dla każdego wymagania
 * @param stillMissing  umiejętności z wymagań, których nadal brakuje (tailoring ich nie dodaje)
 * @param summary       jedno zdanie dla kandydata
 */
public record ScoreComparison(int before, int after, int delta, List<RequirementChange> requirements,
                              List<String> stillMissing, String summary) {

    /** Co się stało z wymaganiem. */
    public enum Change {
        /** wymaganie zyskało dowód w CV (np. umiejętność z profilu dopisana do kluczowych kompetencji) */
        GAINED_EVIDENCE,
        /** więcej punktów, np. pełne dopasowanie zamiast częściowego */
        IMPROVED,
        /** bez zmian */
        UNCHANGED,
        /** nadal niespełnione */
        STILL_MISSING,
        /** mniej punktów (np. zwinięty punkt był dowodem) */
        WORSE
    }

    /**
     * Zmiana jednego wymagania.
     *
     * @param requirement   treść wymagania
     * @param mustHave      obowiązkowe?
     * @param levelBefore   poziom przed
     * @param levelAfter    poziom po
     * @param pointsBefore  punkty przed
     * @param pointsAfter   punkty po
     * @param maxPoints     punkty możliwe
     * @param evidenceAfter cytat-dowód z wersji dopasowanej (dosłowny fragment) lub null
     * @param change        rodzaj zmiany
     * @param note          wyjaśnienie
     */
    public record RequirementChange(String requirement, boolean mustHave, MatchLevel levelBefore,
                                    MatchLevel levelAfter, double pointsBefore, double pointsAfter, double maxPoints,
                                    String evidenceAfter, Change change, String note) {
    }

    static final String NO_NEW_SKILLS = "Tailoring nie dodaje umiejętności, których nie masz w CV ani w profilu.";

    public static ScoreComparison of(MatchResult before, MatchResult after) {
        List<RequirementChange> out = new ArrayList<>();
        Set<String> stillMissing = new LinkedHashSet<>();
        int n = Math.min(before.requirements().size(), after.requirements().size());
        for (int i = 0; i < n; i++) {
            RequirementMatch b = before.requirements().get(i);
            RequirementMatch a = after.requirements().get(i);
            Change change;
            String note;
            if (a.points() < b.points() - 0.01) {
                change = Change.WORSE;
                note = "Wynik spadł — sprawdź, czy zwinięty punkt nie był dowodem dla tego wymagania.";
            } else if (!a.met()) {
                change = Change.STILL_MISSING;
                note = a.note() == null ? NO_NEW_SKILLS : a.note();
                for (String s : a.skills()) {
                    if (!after.matchedSkills().contains(s)) {
                        stillMissing.add(s);
                    }
                }
            } else if (b.evidence() == null && a.evidence() != null) {
                change = Change.GAINED_EVIDENCE;
                note = b.level() == MatchLevel.PROFILE_ONLY
                        ? "Umiejętność z profilu jest teraz widoczna w CV (kluczowe kompetencje / podsumowanie)."
                        : "Wymaganie ma teraz cytat-dowód w CV.";
            } else if (a.points() > b.points() + 0.01) {
                change = Change.IMPROVED;
                note = "Lepsze dopasowanie (" + label(b.level()) + " → " + label(a.level()) + ").";
            } else {
                change = Change.UNCHANGED;
                note = null;
            }
            out.add(new RequirementChange(a.requirement(), a.mustHave(), b.level(), a.level(), b.points(), a.points(),
                    a.maxPoints(), a.evidence(), change, note));
        }
        int delta = after.score() - before.score();
        long gained = out.stream().filter(r -> r.change() == Change.GAINED_EVIDENCE || r.change() == Change.IMPROVED)
                .count();
        long missing = out.stream().filter(r -> r.change() == Change.STILL_MISSING).count();
        StringBuilder sb = new StringBuilder();
        sb.append("Wynik dopasowania: ").append(before.score()).append(" → ").append(after.score()).append(" (")
                .append(delta >= 0 ? "+" : "−").append(Math.abs(delta)).append(" pkt). ");
        if (gained > 0) {
            sb.append("Zyskało wymagań: ").append(gained).append(". ");
        }
        if (missing > 0) {
            sb.append("Nadal niespełnione: ").append(missing).append(" — ")
                    .append(NO_NEW_SKILLS.substring(0, 1).toLowerCase(java.util.Locale.ROOT))
                    .append(NO_NEW_SKILLS.substring(1));
        }
        return new ScoreComparison(before.score(), after.score(), delta, List.copyOf(out), List.copyOf(stillMissing),
                sb.toString().strip());
    }

    private static String label(MatchLevel level) {
        return switch (level) {
            case FULL -> "spełnione";
            case PARTIAL -> "częściowo";
            case RELATED -> "pokrewne";
            case PROFILE_ONLY -> "tylko w profilu";
            case MISSING -> "brak";
        };
    }
}

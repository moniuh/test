package pl.radarpracy.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reguły ostrzeżenia w dzienniku samopoczucia (pomysł #76). Bez porad medycznych — tylko wspierający
 * komunikat z sugestią przerwy i ograniczenia celu.
 *
 * <ul>
 *   <li>A: średnia nastroju lub energii z ostatnich {@link #LAST_ENTRIES} wpisów ≤ {@link #LOW_AVERAGE}.</li>
 *   <li>B: nastrój w najnowszym wpisie jest o co najmniej {@link #DROP_POINTS} pkt niższy niż najwyższy
 *       nastrój z {@link #DROP_WINDOW_DAYS} poprzednich dni.</li>
 * </ul>
 * Reguły działają tylko na świeżych danych: najnowszy wpis nie starszy niż {@link #RECENT_DAYS} dni,
 * a wpisy do średniej — z ostatnich {@link #AVERAGE_WINDOW_DAYS} dni.
 */
public final class MoodRules {

    static final int LAST_ENTRIES = 3;
    static final double LOW_AVERAGE = 2.0;
    static final int DROP_POINTS = 2;
    static final int DROP_WINDOW_DAYS = 7;
    static final int RECENT_DAYS = 7;
    static final int AVERAGE_WINDOW_DAYS = 14;

    static final String SUPPORT = "Jeśli jest Ci naprawdę ciężko, w kryzysie warto porozmawiać z kimś bliskim "
            + "lub ze specjalistą — nie musisz radzić sobie z tym sam(a).";

    private MoodRules() {
    }

    /**
     * Ostrzeżenie dla kandydata.
     *
     * @param reasons       które reguły zadziałały (zdania z liczbami)
     * @param message       wspierający komunikat z sugestią przerwy i ograniczenia celu
     * @param suggestedGoal sugerowany tymczasowy cel tygodniowy
     * @param support       zdanie o rozmowie z bliską osobą lub specjalistą
     */
    public record Warning(List<String> reasons, String message, int suggestedGoal, String support) {
    }

    public static Optional<Warning> evaluate(List<MoodEntry> entries, LocalDate today, Integer weeklyGoal) {
        List<MoodEntry> sorted = entries.stream()
                .filter(e -> e.getDate() != null && !e.getDate().isAfter(today))
                .sorted(Comparator.comparing(MoodEntry::getDate).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        MoodEntry latest = sorted.get(0);
        if (latest.getDate().isBefore(today.minusDays(RECENT_DAYS))) {
            return Optional.empty();
        }
        List<String> reasons = new ArrayList<>();

        List<MoodEntry> lastN = sorted.stream()
                .filter(e -> !e.getDate().isBefore(today.minusDays(AVERAGE_WINDOW_DAYS)))
                .limit(LAST_ENTRIES).toList();
        if (lastN.size() == LAST_ENTRIES) {
            double mood = lastN.stream().mapToInt(MoodEntry::getMood).average().orElse(5);
            double energy = lastN.stream().mapToInt(MoodEntry::getEnergy).average().orElse(5);
            if (mood <= LOW_AVERAGE) {
                reasons.add("Średni nastrój z ostatnich " + LAST_ENTRIES + " wpisów to " + fmt(mood) + " na 5.");
            }
            if (energy <= LOW_AVERAGE) {
                reasons.add("Średnia energia z ostatnich " + LAST_ENTRIES + " wpisów to " + fmt(energy) + " na 5.");
            }
        }

        LocalDate windowStart = latest.getDate().minusDays(DROP_WINDOW_DAYS);
        sorted.stream()
                .filter(e -> e != latest && !e.getDate().isBefore(windowStart) && e.getDate().isBefore(latest.getDate()))
                .max(Comparator.comparingInt(MoodEntry::getMood))
                .filter(peak -> peak.getMood() - latest.getMood() >= DROP_POINTS)
                .ifPresent(peak -> reasons.add("Nastrój spadł z " + peak.getMood() + " do " + latest.getMood()
                        + " w ciągu tygodnia."));

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        int goal = WeeklyGoal.goalOrDefault(weeklyGoal);
        int suggested = Math.max(1, (int) Math.ceil(goal / 2.0));
        String message = "Szukanie pracy bywa wyczerpujące, a gorsze dni są czymś zupełnie normalnym. "
                + "Rozważ dzień lub dwa przerwy i tymczasowe obniżenie celu tygodniowego (np. z " + goal + " do "
                + suggested + " aplikacji) — mniej, ale lepiej dopasowanych aplikacji to też dobra strategia. Zrób coś, co daje Ci "
                + "odpoczynek: spacer, ruch, spotkanie ze znajomymi.";
        return Optional.of(new Warning(List.copyOf(reasons), message, suggested, SUPPORT));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}

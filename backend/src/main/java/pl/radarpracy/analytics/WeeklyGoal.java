package pl.radarpracy.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Cel tygodniowy (pomysł #73): tydzień liczony od poniedziałku do niedzieli, seria kolejnych tygodni
 * z osiągniętym celem. Bieżący, niezakończony tydzień nie przerywa serii — wlicza się dopiero po osiągnięciu celu.
 */
public final class WeeklyGoal {

    /** Domyślny cel, gdy profil go nie określa. */
    static final int DEFAULT_GOAL = 10;
    /** Ile tygodni wstecz sprawdzamy serię (ograniczenie obliczeń). */
    static final int MAX_STREAK_WEEKS = 104;

    private WeeklyGoal() {
    }

    /**
     * Bieżący tydzień.
     *
     * @param start        poniedziałek
     * @param end          niedziela
     * @param applications aplikacje wysłane w tym tygodniu
     * @param goal         cel z profilu
     * @param percent      postęp w % (może przekroczyć 100)
     * @param remaining    ile brakuje do celu (0, gdy osiągnięty)
     * @param daysLeft     ile dni tygodnia zostało łącznie z dziś
     * @param achieved     czy cel osiągnięty
     */
    public record Week(LocalDate start, LocalDate end, int applications, int goal, int percent, int remaining,
                       int daysLeft, boolean achieved) {
    }

    public record WeekBar(LocalDate start, LocalDate end, int applications, boolean achieved) {
    }

    /**
     * @param current     bieżący tydzień
     * @param streak      liczba kolejnych tygodni z osiągniętym celem
     * @param recentWeeks historia ostatnich tygodni (od najstarszego), łącznie z bieżącym
     */
    public record Progress(Week current, int streak, List<WeekBar> recentWeeks) {
    }

    static int goalOrDefault(Integer goal) {
        return goal == null || goal < 1 ? DEFAULT_GOAL : goal;
    }

    static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static Progress compute(List<LocalDate> sentDates, LocalDate today, Integer goalSetting, int historyWeeks) {
        int goal = goalOrDefault(goalSetting);
        LocalDate start = weekStart(today);
        int thisWeek = countIn(sentDates, start);
        int daysLeft = (int) ChronoUnit.DAYS.between(today, start.plusDays(6)) + 1;
        Week current = new Week(start, start.plusDays(6), thisWeek, goal, (int) Math.round(100.0 * thisWeek / goal),
                Math.max(0, goal - thisWeek), daysLeft, thisWeek >= goal);

        int streak = current.achieved() ? 1 : 0;
        LocalDate w = start.minusWeeks(1);
        for (int i = 0; i < MAX_STREAK_WEEKS && countIn(sentDates, w) >= goal; i++) {
            streak++;
            w = w.minusWeeks(1);
        }

        List<WeekBar> bars = new ArrayList<>();
        for (int i = Math.max(1, historyWeeks) - 1; i >= 0; i--) {
            LocalDate s = start.minusWeeks(i);
            int c = countIn(sentDates, s);
            bars.add(new WeekBar(s, s.plusDays(6), c, c >= goal));
        }
        return new Progress(current, streak, bars);
    }

    private static int countIn(List<LocalDate> dates, LocalDate weekStart) {
        LocalDate end = weekStart.plusDays(6);
        return (int) dates.stream().filter(d -> d != null && !d.isBefore(weekStart) && !d.isAfter(end)).count();
    }
}

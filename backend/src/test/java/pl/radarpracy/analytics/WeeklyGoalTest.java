package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyGoalTest {

    /** Środa 28.10.2026; tydzień: pn 26.10 – nd 1.11. */
    static final LocalDate WED = LocalDate.of(2026, 10, 28);

    private static List<LocalDate> times(LocalDate d, int n) {
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(d);
        }
        return out;
    }

    @Test
    void weekIsMondayToSundayAndProgressIsComputed() {
        List<LocalDate> dates = new ArrayList<>(times(LocalDate.of(2026, 10, 26), 2));
        dates.add(LocalDate.of(2026, 11, 1));   // niedziela — ten sam tydzień
        dates.add(LocalDate.of(2026, 10, 25));  // poprzednia niedziela — poza
        WeeklyGoal.Progress p = WeeklyGoal.compute(dates, WED, 4, 8);
        assertThat(p.current().start()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(p.current().end()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(p.current().applications()).isEqualTo(3);
        assertThat(p.current().percent()).isEqualTo(75);
        assertThat(p.current().remaining()).isEqualTo(1);
        assertThat(p.current().daysLeft()).isEqualTo(5);
        assertThat(p.recentWeeks()).hasSize(8);
        assertThat(p.recentWeeks().get(7).start()).isEqualTo(p.current().start());
    }

    @Test
    void unfinishedWeekDoesNotBreakStreak() {
        List<LocalDate> dates = new ArrayList<>();
        dates.addAll(times(LocalDate.of(2026, 10, 19), 3)); // poprzedni tydzień: 3
        dates.addAll(times(LocalDate.of(2026, 10, 13), 3)); // 2 tygodnie temu: 3
        dates.addAll(times(LocalDate.of(2026, 10, 6), 1));  // 3 tygodnie temu: 1 — przerywa
        dates.add(WED);                                       // bieżący: 1 (w toku)
        WeeklyGoal.Progress p = WeeklyGoal.compute(dates, WED, 3, 4);
        assertThat(p.streak()).isEqualTo(2);
        assertThat(p.current().achieved()).isFalse();

        dates.addAll(times(WED, 2)); // bieżący osiągnięty → seria 3
        assertThat(WeeklyGoal.compute(dates, WED, 3, 4).streak()).isEqualTo(3);
    }

    @Test
    void missingGoalFallsBackToDefault() {
        WeeklyGoal.Progress p = WeeklyGoal.compute(List.of(), WED, null, 1);
        assertThat(p.current().goal()).isEqualTo(WeeklyGoal.DEFAULT_GOAL);
        assertThat(p.streak()).isZero();
        assertThat(WeeklyGoal.compute(List.of(), WED, 0, 1).current().goal()).isEqualTo(WeeklyGoal.DEFAULT_GOAL);
    }
}

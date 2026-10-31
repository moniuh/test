package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MoodRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private static MoodEntry e(int daysAgo, int mood, int energy) {
        return new MoodEntry(TODAY.minusDays(daysAgo), mood, energy, null);
    }

    @Test
    void lowAverageOfLastThreeEntriesWarnsWithSupportiveMessage() {
        Optional<MoodRules.Warning> w = MoodRules.evaluate(List.of(e(0, 2, 3), e(1, 1, 3), e(2, 3, 3), e(5, 5, 5)),
                TODAY, 8);
        assertThat(w).isPresent();
        assertThat(w.get().reasons()).anyMatch(r -> r.contains("Średni nastrój") && r.contains("2,0"));
        assertThat(w.get().suggestedGoal()).isEqualTo(4);
        assertThat(w.get().message()).contains("przerwy").contains("z 8 do 4");
        assertThat(w.get().support()).contains("kimś bliskim").contains("specjalistą");
    }

    @Test
    void lowEnergyAlsoWarns() {
        Optional<MoodRules.Warning> w = MoodRules.evaluate(List.of(e(0, 4, 2), e(1, 4, 2), e(2, 4, 1)), TODAY, 10);
        assertThat(w).isPresent();
        assertThat(w.get().reasons()).anyMatch(r -> r.contains("energia"));
    }

    @Test
    void dropOfTwoPointsWithinAWeekWarns() {
        Optional<MoodRules.Warning> w = MoodRules.evaluate(List.of(e(0, 3, 4), e(4, 5, 4)), TODAY, 10);
        assertThat(w).isPresent();
        assertThat(w.get().reasons()).containsExactly("Nastrój spadł z 5 do 3 w ciągu tygodnia.");
    }

    @Test
    void noWarningForStableOrGoodMood() {
        assertThat(MoodRules.evaluate(List.of(e(0, 4, 4), e(1, 3, 3), e(2, 4, 3)), TODAY, 10)).isEmpty();
        assertThat(MoodRules.evaluate(List.of(), TODAY, 10)).isEmpty();
        // spadek o 2 pkt, ale z wpisu sprzed ponad tygodnia
        assertThat(MoodRules.evaluate(List.of(e(0, 3, 4), e(9, 5, 4)), TODAY, 10)).isEmpty();
    }

    @Test
    void staleEntriesDoNotTriggerWarning() {
        assertThat(MoodRules.evaluate(List.of(e(10, 1, 1), e(11, 1, 1), e(12, 1, 1)), TODAY, 10)).isEmpty();
    }

    @Test
    void fewerThanThreeEntriesDoNotUseAverageRule() {
        assertThat(MoodRules.evaluate(List.of(e(0, 1, 1), e(1, 2, 2)), TODAY, 10)).isEmpty();
    }
}

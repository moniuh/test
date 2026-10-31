package pl.radarpracy.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    @Test
    void daysFallBackToDefault() {
        assertThat(FollowUpPolicy.days(null)).isEqualTo(7);
        assertThat(FollowUpPolicy.days(0)).isEqualTo(7);
        assertThat(FollowUpPolicy.days(10)).isEqualTo(10);
    }

    @Test
    void firstFollowUpCountsFromAppliedDate() {
        assertThat(FollowUpPolicy.afterApplied(LocalDate.of(2026, 10, 20), TODAY, 7)).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(FollowUpPolicy.afterApplied(null, TODAY, 5)).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void responseAndClosingClearFollowUp() {
        assertThat(FollowUpPolicy.clears(ApplicationStatus.SCREENING)).isTrue();
        assertThat(FollowUpPolicy.clears(ApplicationStatus.REJECTED)).isTrue();
        assertThat(FollowUpPolicy.clears(ApplicationStatus.NO_RESPONSE)).isTrue();
        assertThat(FollowUpPolicy.clears(ApplicationStatus.APPLIED)).isFalse();
        assertThat(FollowUpPolicy.clears(ApplicationStatus.PLANNED)).isFalse();
        assertThat(FollowUpPolicy.schedules(ApplicationStatus.APPLIED)).isTrue();
        assertThat(FollowUpPolicy.schedules(ApplicationStatus.PLANNED)).isFalse();
    }

    @Test
    void snoozeStartsFromTodayWhenOverdueAndFromDueDateOtherwise() {
        assertThat(FollowUpPolicy.snooze(LocalDate.of(2026, 10, 25), TODAY, 3)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(FollowUpPolicy.snooze(LocalDate.of(2026, 11, 2), TODAY, 3)).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(FollowUpPolicy.snooze(null, TODAY, 1)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThatThrownBy(() -> FollowUpPolicy.snooze(TODAY, TODAY, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FollowUpPolicy.snooze(TODAY, TODAY, 91)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boardOrderInsertsAndClamps() {
        List<String> col = List.of("a", "b", "c");
        assertThat(BoardOrder.insert(col, "x", 0)).containsExactly("x", "a", "b", "c");
        assertThat(BoardOrder.insert(col, "x", 2)).containsExactly("a", "b", "x", "c");
        assertThat(BoardOrder.insert(col, "x", 99)).containsExactly("a", "b", "c", "x");
        assertThat(BoardOrder.insert(col, "x", -5)).containsExactly("x", "a", "b", "c");
        assertThat(BoardOrder.insert(col, "x", null)).containsExactly("a", "b", "c", "x");
        // przesunięcie w obrębie tej samej kolumny
        assertThat(BoardOrder.insert(col, "c", 0)).containsExactly("c", "a", "b");
        assertThat(BoardOrder.insert(col, "a", 2)).containsExactly("b", "c", "a");
    }

    @Test
    void sentStatusesAndLabels() {
        assertThat(ApplicationService.isSent(ApplicationStatus.PLANNED)).isFalse();
        assertThat(ApplicationService.isSent(ApplicationStatus.WITHDRAWN)).isFalse();
        assertThat(ApplicationService.isSent(ApplicationStatus.APPLIED)).isTrue();
        assertThat(ApplicationService.isSent(ApplicationStatus.INTERVIEW)).isTrue();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            assertThat(StatusLabels.status(s)).isNotBlank();
        }
        for (EventType t : EventType.values()) {
            assertThat(StatusLabels.event(t)).isNotBlank();
        }
        for (InterviewKind k : InterviewKind.values()) {
            assertThat(StatusLabels.kind(k)).isNotBlank();
        }
    }
}

package pl.radarpracy.application.timeline;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewClock;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.application.timeline.TimelineItem.TimelineKind;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.event;
import static pl.radarpracy.application.TestData.interview;

class TimelineServiceTest {

    private final InterviewClock clock = new InterviewClock(ZoneId.of("Europe/Warsaw"),
            Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void mergesEventsInterviewsAndNotesChronologically() {
        ApplicationEvent created = event(1, 9, EventType.CREATED, null, Instant.parse("2026-10-10T08:00:00Z"));
        created.setToStatus(ApplicationStatus.APPLIED);
        ApplicationEvent status = event(2, 9, EventType.STATUS_CHANGED, "Telefon od rekruterki",
                Instant.parse("2026-10-20T08:00:00Z"));
        status.setFromStatus(ApplicationStatus.APPLIED);
        status.setToStatus(ApplicationStatus.SCREENING);
        ApplicationEvent note = event(3, 9, EventType.NOTE, "Przygotować pytania o zespół",
                Instant.parse("2026-10-25T08:00:00Z"));
        Interview past = interview(5, 9, LocalDateTime.of(2026, 10, 22, 10, 0));
        past.setKind(InterviewKind.HR);
        past.setInterviewers("Katarzyna W.");
        Interview future = interview(6, 9, LocalDateTime.of(2026, 11, 3, 14, 0));
        future.setKind(InterviewKind.TECHNICAL);
        future.setDurationMinutes(90);

        List<TimelineItem> items = TimelineService.merge(List.of(note, status, created), List.of(future, past), clock);

        assertThat(items).extracting(TimelineItem::key)
                .containsExactly("event-1", "event-2", "interview-5", "event-3", "interview-6");
        assertThat(items.get(0).title()).isEqualTo("Utworzono aplikację (Wysłana)");
        assertThat(items.get(1).title()).isEqualTo("Zmiana statusu: Wysłana → Pierwszy kontakt");
        assertThat(items.get(1).detail()).isEqualTo("Telefon od rekruterki");
        assertThat(items.get(2).kind()).isEqualTo(TimelineKind.INTERVIEW);
        assertThat(items.get(2).upcoming()).isFalse();
        assertThat(items.get(2).title()).contains("HR").contains("odbyta");
        assertThat(items.get(2).detail()).contains("Prowadzący: Katarzyna W.");
        // 10:00 w Warszawie (CEST do 25.10) = 08:00 UTC
        assertThat(items.get(2).at()).isEqualTo(Instant.parse("2026-10-22T08:00:00Z"));
        assertThat(items.get(3).kind()).isEqualTo(TimelineKind.NOTE);
        assertThat(items.get(4).upcoming()).isTrue();
        assertThat(items.get(4).title()).contains("zaplanowana");
        assertThat(items.get(4).detail()).startsWith("90 min");
    }
}

package pl.radarpracy.application.timeline;

import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.InterviewKind;

import java.time.Instant;

/**
 * Pozycja osi czasu aplikacji (pomysł #40).
 *
 * @param key         unikalny klucz pozycji (np. {@code event-12}, {@code interview-3})
 * @param kind        rodzaj: zdarzenie, notatka lub rozmowa
 * @param at          moment zdarzenia (dla rozmowy — termin rozmowy)
 * @param title       krótki opis po polsku
 * @param detail      szczegóły (notatka, miejsce rozmowy, prowadzący) lub null
 * @param upcoming    dla rozmów: czy termin jest w przyszłości
 */
public record TimelineItem(String key, TimelineKind kind, Instant at, String title, String detail,
                           EventType eventType, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                           Long eventId, Long interviewId, InterviewKind interviewKind, Integer durationMinutes,
                           boolean upcoming) {

    public enum TimelineKind {
        EVENT, NOTE, INTERVIEW
    }
}

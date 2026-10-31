package pl.radarpracy.application.timeline;

import org.springframework.stereotype.Service;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewClock;
import pl.radarpracy.application.StatusLabels;
import pl.radarpracy.application.timeline.TimelineItem.TimelineKind;
import pl.radarpracy.common.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/** Oś czasu aplikacji: zdarzenia, rozmowy (zaplanowane i odbyte) i notatki w jednym widoku (pomysł #40). */
@Service
public class TimelineService {

    private final ApplicationService applications;
    private final InterviewClock clock;

    public TimelineService(ApplicationService applications, InterviewClock clock) {
        this.applications = applications;
        this.clock = clock;
    }

    public List<TimelineItem> timeline(Long applicationId) {
        List<ApplicationEvent> events = applications.events(applicationId);
        List<Interview> interviews = applications.interviews(applicationId);
        return merge(events, interviews, clock);
    }

    /** Scalenie i sortowanie (rosnąco po czasie; przy remisie: zdarzenia, notatki, rozmowy). */
    static List<TimelineItem> merge(List<ApplicationEvent> events, List<Interview> interviews, InterviewClock clock) {
        List<TimelineItem> out = new ArrayList<>();
        for (ApplicationEvent e : events) {
            out.add(fromEvent(e));
        }
        Instant now = clock.now();
        for (Interview i : interviews) {
            out.add(fromInterview(i, clock.toInstant(i.getScheduledAt()), now));
        }
        out.sort(Comparator.comparing(TimelineItem::at)
                .thenComparing(TimelineItem::kind)
                .thenComparing(TimelineItem::key));
        return out;
    }

    static TimelineItem fromEvent(ApplicationEvent e) {
        boolean note = e.getType() == EventType.NOTE;
        String title = switch (e.getType()) {
            case CREATED -> e.getToStatus() == null ? "Utworzono aplikację"
                    : "Utworzono aplikację (" + StatusLabels.status(e.getToStatus()) + ")";
            case STATUS_CHANGED -> "Zmiana statusu: " + StatusLabels.status(e.getFromStatus()) + " → "
                    + StatusLabels.status(e.getToStatus());
            default -> StatusLabels.event(e.getType());
        };
        return new TimelineItem("event-" + e.getId(), note ? TimelineKind.NOTE : TimelineKind.EVENT,
                e.getOccurredAt(), title, Text.isBlank(e.getNote()) ? null : e.getNote(), e.getType(),
                e.getFromStatus(), e.getToStatus(), e.getId(), null, null, null, false);
    }

    static TimelineItem fromInterview(Interview i, Instant at, Instant now) {
        boolean upcoming = at.isAfter(now);
        String title = (i.getKind() == null ? "Rozmowa" : "Rozmowa — " + StatusLabels.kind(i.getKind()))
                + (upcoming ? " (zaplanowana)" : " (odbyta)");
        StringJoiner detail = new StringJoiner(" · ");
        if (i.getDurationMinutes() != null) {
            detail.add(i.getDurationMinutes() + " min");
        }
        if (!Text.isBlank(i.getLocation())) {
            detail.add("Miejsce: " + i.getLocation());
        }
        if (!Text.isBlank(i.getInterviewers())) {
            detail.add("Prowadzący: " + i.getInterviewers());
        }
        return new TimelineItem("interview-" + i.getId(), TimelineKind.INTERVIEW, at, title,
                detail.length() == 0 ? null : detail.toString(), null, null, null, null, i.getId(), i.getKind(),
                i.getDurationMinutes(), upcoming);
    }
}

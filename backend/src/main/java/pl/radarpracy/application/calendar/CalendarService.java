package pl.radarpracy.application.calendar;

import org.springframework.stereotype.Service;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewClock;
import pl.radarpracy.application.InterviewRepository;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.application.StatusLabels;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Eksport rozmów do kalendarza w formacie iCalendar (pomysł #45). */
@Service
public class CalendarService {

    static final int DEFAULT_DURATION = 60;
    static final int ALARM_MINUTES = 60;

    private final InterviewRepository interviews;
    private final ApplicationService applications;
    private final InterviewClock clock;

    public CalendarService(InterviewRepository interviews, ApplicationService applications, InterviewClock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.clock = clock;
    }

    /** Jedna rozmowa jako plik .ics. */
    public String single(Long interviewId) {
        Interview i = interviews.findById(interviewId).orElseThrow(() -> NotFoundException.of("Rozmowa", interviewId));
        JobApplication a = applications.get(i.getApplicationId());
        return IcsWriter.calendar(null, List.of(event(i, a, clock)), clock.now());
    }

    /** Nadchodzące rozmowy (od początku dzisiejszego dnia w strefie kandydata). */
    public String upcoming() {
        LocalDateTime from = clock.localNow().toLocalDate().atStartOfDay();
        List<IcsWriter.Event> events = new ArrayList<>();
        for (Interview i : interviews.findAllByOrderByScheduledAtAsc()) {
            if (i.getScheduledAt() == null || i.getScheduledAt().isBefore(from)) {
                continue;
            }
            JobApplication a;
            try {
                a = applications.get(i.getApplicationId());
            } catch (NotFoundException e) {
                continue;
            }
            events.add(event(i, a, clock));
        }
        return IcsWriter.calendar("Radar Pracy — rozmowy", events, clock.now());
    }

    static IcsWriter.Event event(Interview i, JobApplication a, InterviewClock clock) {
        JobOffer o = a.getOffer();
        Instant start = clock.toInstant(i.getScheduledAt());
        int minutes = i.getDurationMinutes() == null || i.getDurationMinutes() <= 0 ? DEFAULT_DURATION
                : i.getDurationMinutes();
        String company = Text.isBlank(o.getCompany()) ? null : o.getCompany();
        String summary = "Rozmowa (" + StatusLabels.kind(i.getKind()) + "): " + o.getTitle()
                + (company == null ? "" : " — " + company);
        StringJoiner d = new StringJoiner("\n");
        d.add("Stanowisko: " + o.getTitle());
        if (company != null) {
            d.add("Firma: " + company);
        }
        d.add("Rodzaj rozmowy: " + StatusLabels.kind(i.getKind()) + ", " + minutes + " min");
        if (!Text.isBlank(i.getInterviewers())) {
            d.add("Prowadzący: " + i.getInterviewers());
        }
        String contact = contact(a);
        if (contact != null) {
            d.add("Kontakt: " + contact);
        }
        if (!Text.isBlank(o.getUrl())) {
            d.add("Ogłoszenie: " + o.getUrl());
        }
        d.add("Dodane w Radarze Pracy (aplikacja #" + a.getId() + ").");
        return new IcsWriter.Event("interview-" + i.getId() + "@radarpracy", start, start.plusSeconds(minutes * 60L),
                summary, i.getLocation(), d.toString(), o.getUrl(), ALARM_MINUTES);
    }

    private static String contact(JobApplication a) {
        boolean name = !Text.isBlank(a.getContactName());
        boolean mail = !Text.isBlank(a.getContactEmail());
        if (name && mail) {
            return a.getContactName() + " <" + a.getContactEmail() + ">";
        }
        return name ? a.getContactName() : mail ? a.getContactEmail() : null;
    }
}

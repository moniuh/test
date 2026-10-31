package pl.radarpracy.analytics;

import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.offer.JobOffer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Budowanie danych testowych bez bazy. */
final class Fixtures {

    static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final AtomicLong IDS = new AtomicLong(1000);

    private Fixtures() {
    }

    static JobOffer offer(String title, String company) {
        JobOffer o = new JobOffer();
        o.setId(IDS.incrementAndGet());
        o.setTitle(title);
        o.setCompany(company);
        return o;
    }

    /** Budowniczy jednej aplikacji z historią. */
    static final class App {
        final JobApplication a = new JobApplication();
        final List<ApplicationEvent> events = new ArrayList<>();
        final List<Interview> interviews = new ArrayList<>();

        App(ApplicationStatus status, LocalDate appliedAt) {
            a.setId(IDS.incrementAndGet());
            a.setOffer(offer("Java Developer", "Firma " + a.getId()));
            a.setStatus(status);
            a.setAppliedAt(appliedAt);
        }

        App source(String source) {
            a.setSource(source);
            return this;
        }

        App cv(String name) {
            a.setCvVariantName(name);
            return this;
        }

        App company(String company) {
            a.getOffer().setCompany(company);
            return this;
        }

        App firstResponse(LocalDate d) {
            a.setFirstResponseAt(d);
            return this;
        }

        App followUp(LocalDate d) {
            a.setNextFollowUpAt(d);
            return this;
        }

        App event(EventType type, ApplicationStatus from, ApplicationStatus to, LocalDate when) {
            ApplicationEvent e = new ApplicationEvent(a.getId(), type, null);
            e.setFromStatus(from);
            e.setToStatus(to);
            e.setOccurredAt(when.atTime(10, 0).atZone(ZONE).toInstant());
            events.add(e);
            return this;
        }

        App interview(LocalDateTime at, InterviewKind kind, boolean debriefed) {
            Interview i = new Interview();
            i.setId(IDS.incrementAndGet());
            i.setApplicationId(a.getId());
            i.setScheduledAt(at);
            i.setKind(kind);
            i.setDurationMinutes(60);
            if (debriefed) {
                i.setDebriefedAt(at.plusHours(2).atZone(ZONE).toInstant());
            }
            interviews.add(i);
            return this;
        }

        AppFacts facts() {
            return AppFacts.of(a, events, interviews, ZONE);
        }
    }

    static App app(ApplicationStatus status, LocalDate appliedAt) {
        return new App(status, appliedAt);
    }
}

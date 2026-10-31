package pl.radarpracy.application;

import pl.radarpracy.offer.JobOffer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Budowanie obiektów do testów jednostkowych modułu aplikacji (bez bazy). */
public final class TestData {

    private TestData() {
    }

    public static JobOffer offer(long id, String title, String company) {
        JobOffer o = new JobOffer();
        o.setId(id);
        o.setTitle(title);
        o.setCompany(company);
        return o;
    }

    public static JobApplication application(long id, JobOffer offer, ApplicationStatus status, LocalDate appliedAt) {
        JobApplication a = new JobApplication();
        a.setId(id);
        a.setOffer(offer);
        a.setStatus(status);
        a.setAppliedAt(appliedAt);
        a.setCreatedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return a;
    }

    public static Interview interview(long id, long applicationId, LocalDateTime at) {
        Interview i = new Interview();
        i.setId(id);
        i.setApplicationId(applicationId);
        i.setScheduledAt(at);
        return i;
    }

    public static ApplicationEvent event(long id, long applicationId, EventType type, String note, Instant at) {
        ApplicationEvent e = new ApplicationEvent(applicationId, type, note);
        e.setId(id);
        e.setOccurredAt(at);
        return e;
    }
}

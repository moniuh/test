package pl.radarpracy.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Zdarzenie na osi czasu aplikacji. */
@Entity
@Table(name = "application_event", indexes = @Index(columnList = "applicationId"))
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ApplicationStatus toStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private Instant occurredAt;

    public ApplicationEvent() {
    }

    public ApplicationEvent(Long applicationId, EventType type, String note) {
        this.applicationId = applicationId;
        this.type = type;
        this.note = note;
        this.occurredAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ApplicationStatus fromStatus) { this.fromStatus = fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public void setToStatus(ApplicationStatus toStatus) { this.toStatus = toStatus; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

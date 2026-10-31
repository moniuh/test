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
import java.time.LocalDateTime;

/** Rozmowa rekrutacyjna w ramach aplikacji (termin, notatki, debrief). */
@Entity
@Table(name = "interview", indexes = @Index(columnList = "applicationId"))
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;
    private Integer durationMinutes = 60;

    @Enumerated(EnumType.STRING)
    private InterviewKind kind = InterviewKind.VIDEO;

    /** Adres lub link do spotkania. */
    @Column(length = 1000)
    private String location;
    /** Osoby prowadzące. */
    private String interviewers;

    /** Notatki z rozmowy (pomysł #52). */
    @Column(columnDefinition = "TEXT")
    private String notes;
    /** Pytania, które padły. */
    @Column(columnDefinition = "TEXT")
    private String questionsAsked;

    // --- debrief (pomysł #62) ---
    @Column(columnDefinition = "TEXT")
    private String wentWell;
    @Column(columnDefinition = "TEXT")
    private String toImprove;
    /** Samoocena 1–5. */
    private Integer selfRating;
    private Instant debriefedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public InterviewKind getKind() { return kind; }
    public void setKind(InterviewKind kind) { this.kind = kind; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getInterviewers() { return interviewers; }
    public void setInterviewers(String interviewers) { this.interviewers = interviewers; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getQuestionsAsked() { return questionsAsked; }
    public void setQuestionsAsked(String questionsAsked) { this.questionsAsked = questionsAsked; }
    public String getWentWell() { return wentWell; }
    public void setWentWell(String wentWell) { this.wentWell = wentWell; }
    public String getToImprove() { return toImprove; }
    public void setToImprove(String toImprove) { this.toImprove = toImprove; }
    public Integer getSelfRating() { return selfRating; }
    public void setSelfRating(Integer selfRating) { this.selfRating = selfRating; }
    public Instant getDebriefedAt() { return debriefedAt; }
    public void setDebriefedAt(Instant debriefedAt) { this.debriefedAt = debriefedAt; }
}

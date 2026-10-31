package pl.radarpracy.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pl.radarpracy.offer.JobOffer;

import java.time.Instant;
import java.time.LocalDate;

/** Aplikacja kandydata na ofertę. */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "offer_id")
    private JobOffer offer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    private LocalDate appliedAt;
    /** Kanał aplikacji, np. „pracuj.pl”, „LinkedIn”, „polecenie”, „strona firmy”. */
    private String source;

    private Long cvVariantId;
    private String cvVariantName;
    /** Dokładna treść CV wysłanego do firmy (snapshot). */
    @Column(columnDefinition = "TEXT")
    private String cvSnapshot;
    /** Dokładna treść wysłanego listu motywacyjnego. */
    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    private LocalDate nextFollowUpAt;
    private LocalDate lastContactAt;
    /** Data pierwszej odpowiedzi firmy (do lejka i czasu odpowiedzi). */
    private LocalDate firstResponseAt;

    /** Kolejność w kolumnie Kanban. */
    private Integer boardPosition = 0;

    private String contactName;
    private String contactEmail;
    private Integer salaryExpectation;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public JobOffer getOffer() { return offer; }
    public void setOffer(JobOffer offer) { this.offer = offer; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public LocalDate getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDate appliedAt) { this.appliedAt = appliedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getCvVariantId() { return cvVariantId; }
    public void setCvVariantId(Long cvVariantId) { this.cvVariantId = cvVariantId; }
    public String getCvVariantName() { return cvVariantName; }
    public void setCvVariantName(String cvVariantName) { this.cvVariantName = cvVariantName; }
    public String getCvSnapshot() { return cvSnapshot; }
    public void setCvSnapshot(String cvSnapshot) { this.cvSnapshot = cvSnapshot; }
    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    public LocalDate getNextFollowUpAt() { return nextFollowUpAt; }
    public void setNextFollowUpAt(LocalDate nextFollowUpAt) { this.nextFollowUpAt = nextFollowUpAt; }
    public LocalDate getLastContactAt() { return lastContactAt; }
    public void setLastContactAt(LocalDate lastContactAt) { this.lastContactAt = lastContactAt; }
    public LocalDate getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(LocalDate firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public Integer getBoardPosition() { return boardPosition; }
    public void setBoardPosition(Integer boardPosition) { this.boardPosition = boardPosition; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public Integer getSalaryExpectation() { return salaryExpectation; }
    public void setSalaryExpectation(Integer salaryExpectation) { this.salaryExpectation = salaryExpectation; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package pl.radarpracy.tailoring;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Powiązanie dopasowanej wersji CV z ofertą i CV bazowym (pomysł #94). Sama treść CV to zwykły
 * {@code CvVariant} z {@code origin} zaczynającym się od „dopasowane”.
 */
@Entity
@Table(name = "tailoring_cv")
public class TailoredCv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long cvVariantId;

    private Long baseCvId;
    /** Nazwa CV bazowego w chwili dopasowania (na wypadek jego usunięcia). */
    private String baseCvName;

    @Column(nullable = false)
    private Long offerId;
    /** Tytuł i firma oferty w chwili dopasowania (na wypadek usunięcia oferty). */
    private String offerTitle;
    private String offerCompany;

    private int scoreBefore;
    private int scoreAfter;

    @Convert(converter = GuardReportConverter.class)
    @Column(columnDefinition = "TEXT")
    private GuardReport guardReport;

    /** „ai” albo „offline”. */
    @Column(nullable = false)
    private String source = "offline";
    /** Czy kandydat edytował tekst przed zapisem. */
    private boolean edited;
    /** Czy zapisano mimo naruszeń zgłoszonych przez strażnika (jawne potwierdzenie). */
    private boolean acceptedViolations;

    @Convert(converter = TailoringOptionsConverter.class)
    @Column(columnDefinition = "TEXT")
    private TailoringOptions options;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCvVariantId() { return cvVariantId; }
    public void setCvVariantId(Long cvVariantId) { this.cvVariantId = cvVariantId; }
    public Long getBaseCvId() { return baseCvId; }
    public void setBaseCvId(Long baseCvId) { this.baseCvId = baseCvId; }
    public String getBaseCvName() { return baseCvName; }
    public void setBaseCvName(String baseCvName) { this.baseCvName = baseCvName; }
    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }
    public String getOfferTitle() { return offerTitle; }
    public void setOfferTitle(String offerTitle) { this.offerTitle = offerTitle; }
    public String getOfferCompany() { return offerCompany; }
    public void setOfferCompany(String offerCompany) { this.offerCompany = offerCompany; }
    public int getScoreBefore() { return scoreBefore; }
    public void setScoreBefore(int scoreBefore) { this.scoreBefore = scoreBefore; }
    public int getScoreAfter() { return scoreAfter; }
    public void setScoreAfter(int scoreAfter) { this.scoreAfter = scoreAfter; }
    public GuardReport getGuardReport() { return guardReport; }
    public void setGuardReport(GuardReport guardReport) { this.guardReport = guardReport; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isEdited() { return edited; }
    public void setEdited(boolean edited) { this.edited = edited; }
    public boolean isAcceptedViolations() { return acceptedViolations; }
    public void setAcceptedViolations(boolean acceptedViolations) { this.acceptedViolations = acceptedViolations; }
    public TailoringOptions getOptions() { return options; }
    public void setOptions(TailoringOptions options) { this.options = options; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

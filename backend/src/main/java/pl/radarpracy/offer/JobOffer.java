package pl.radarpracy.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Oferta pracy zapisana przez kandydata. */
@Entity
@Table(name = "job_offer")
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    private String company;
    private String location;
    @Column(length = 2000)
    private String url;
    /** Źródło oferty, np. „pracuj.pl”, „justjoin.it”, „LinkedIn”, „polecenie”, „strona firmy”. */
    private String source;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    @Convert(converter = ContractTypeListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<ContractType> contractTypes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Seniority seniority;

    private Integer salaryMin;
    private Integer salaryMax;
    private String salaryCurrency = "PLN";
    @Enumerated(EnumType.STRING)
    private SalaryPeriod salaryPeriod;
    /** true = brutto (UoP) / netto+VAT dla B2B traktujemy jako kwotę kontraktu; false = netto „na rękę”; null = nieznane. */
    private Boolean salaryGross;

    @Convert(converter = RequirementListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Requirement> requirements = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> benefits = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status = OfferStatus.NEW;

    @Enumerated(EnumType.STRING)
    private RejectReason rejectReason;
    @Column(length = 1000)
    private String rejectNote;

    /** Jeśli oferta jest duplikatem innej — id oryginału. */
    private Long duplicateOfId;

    private LocalDate publishedAt;
    private LocalDate expiresAt;

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

    // --- gettery i settery ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public List<ContractType> getContractTypes() { return contractTypes; }
    public void setContractTypes(List<ContractType> contractTypes) { this.contractTypes = contractTypes == null ? new ArrayList<>() : new ArrayList<>(contractTypes); }
    public Seniority getSeniority() { return seniority; }
    public void setSeniority(Seniority seniority) { this.seniority = seniority; }
    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }
    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(SalaryPeriod salaryPeriod) { this.salaryPeriod = salaryPeriod; }
    public Boolean getSalaryGross() { return salaryGross; }
    public void setSalaryGross(Boolean salaryGross) { this.salaryGross = salaryGross; }
    public List<Requirement> getRequirements() { return requirements; }
    public void setRequirements(List<Requirement> requirements) { this.requirements = requirements == null ? new ArrayList<>() : new ArrayList<>(requirements); }
    public List<String> getBenefits() { return benefits; }
    public void setBenefits(List<String> benefits) { this.benefits = benefits == null ? new ArrayList<>() : new ArrayList<>(benefits); }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }
    public RejectReason getRejectReason() { return rejectReason; }
    public void setRejectReason(RejectReason rejectReason) { this.rejectReason = rejectReason; }
    public String getRejectNote() { return rejectNote; }
    public void setRejectNote(String rejectNote) { this.rejectNote = rejectNote; }
    public Long getDuplicateOfId() { return duplicateOfId; }
    public void setDuplicateOfId(Long duplicateOfId) { this.duplicateOfId = duplicateOfId; }
    public LocalDate getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

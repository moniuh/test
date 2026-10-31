package pl.radarpracy.matching.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.offer.WorkModeListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Zapisane wyszukiwanie z alertem o nowych ofertach (pomysł #33). Kryteria odpowiadają
 * {@link RankingQuery}; „nowe” to oferty dodane po {@link #lastCheckedAt}.
 */
@Entity
@Table(name = "ranking_saved_search")
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Fraza wyszukiwania (każde słowo musi wystąpić w ogłoszeniu). */
    @Column(name = "search_query", length = 500)
    private String query;

    /** Minimalne dopasowanie do CV 0–100. */
    private Integer minScore;

    /** Minimalne wynagrodzenie (równoważnik UoP brutto). */
    private Integer minSalary;

    @Convert(converter = WorkModeListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<WorkMode> workModes = new ArrayList<>();

    @Convert(converter = OfferStatusListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<OfferStatus> statuses = new ArrayList<>();

    private boolean includeExcluded;

    /** Wersja CV do dopasowania (null = aktywna). */
    private Long cvId;

    /** Kiedy kandydat ostatnio przejrzał wyniki — późniejsze oferty są „nowe”. */
    private Instant lastCheckedAt;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (lastCheckedAt == null) {
            lastCheckedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getMinScore() { return minScore; }
    public void setMinScore(Integer minScore) { this.minScore = minScore; }
    public Integer getMinSalary() { return minSalary; }
    public void setMinSalary(Integer minSalary) { this.minSalary = minSalary; }
    public List<WorkMode> getWorkModes() { return workModes; }
    public void setWorkModes(List<WorkMode> workModes) { this.workModes = workModes == null ? new ArrayList<>() : new ArrayList<>(workModes); }
    public List<OfferStatus> getStatuses() { return statuses; }
    public void setStatuses(List<OfferStatus> statuses) { this.statuses = statuses == null ? new ArrayList<>() : new ArrayList<>(statuses); }
    public boolean isIncludeExcluded() { return includeExcluded; }
    public void setIncludeExcluded(boolean includeExcluded) { this.includeExcluded = includeExcluded; }
    public Long getCvId() { return cvId; }
    public void setCvId(Long cvId) { this.cvId = cvId; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

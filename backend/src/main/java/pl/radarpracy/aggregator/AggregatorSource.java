package pl.radarpracy.aggregator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Źródło ofert obserwowane przez agregator (kanał RSS/Atom, strona kariery, plik demonstracyjny). */
@Entity
@Table(name = "aggregator_source")
public class AggregatorSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType type;

    /** Adres kanału / strony albo nazwa pliku w {@code classpath:aggregator/} dla typu DEMO. */
    @Column(length = 2000, nullable = false)
    private String url;

    private boolean enabled = true;

    // --- filtry (pomysł #60) ---
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> includeKeywords = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> excludeKeywords = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> locations = new ArrayList<>();

    /** Minimalne wynagrodzenie miesięczne (równoważnik UoP brutto, PLN). */
    private Integer minSalary;

    // --- stan ---
    private Instant lastRunAt;
    @Enumerated(EnumType.STRING)
    private RunStatus lastStatus;
    @Column(length = 2000)
    private String lastError;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public SourceType getType() { return type; }
    public void setType(SourceType type) { this.type = type; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getIncludeKeywords() { return includeKeywords; }
    public void setIncludeKeywords(List<String> v) { this.includeKeywords = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> getExcludeKeywords() { return excludeKeywords; }
    public void setExcludeKeywords(List<String> v) { this.excludeKeywords = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> v) { this.locations = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public Integer getMinSalary() { return minSalary; }
    public void setMinSalary(Integer minSalary) { this.minSalary = minSalary; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public RunStatus getLastStatus() { return lastStatus; }
    public void setLastStatus(RunStatus lastStatus) { this.lastStatus = lastStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Filtry źródła jako wartość (do oceny pozycji). */
    public SourceFilters filters() {
        return new SourceFilters(includeKeywords, excludeKeywords, locations, minSalary);
    }
}

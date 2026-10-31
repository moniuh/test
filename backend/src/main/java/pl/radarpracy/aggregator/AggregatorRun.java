package pl.radarpracy.aggregator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Jeden przebieg pobierania źródła (historia, pomysł #15). */
@Entity
@Table(name = "aggregator_run")
public class AggregatorRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sourceId;
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger")
    private RunTrigger trigger;

    private Instant startedAt;
    private Instant finishedAt;

    /** pozycje odczytane ze źródła */
    private int fetched;
    /** nowe oferty zapisane w bazie */
    private int created;
    /** pominięte duplikaty */
    private int duplicates;
    /** pominięte przez filtry źródła */
    private int filteredOut;
    /** pozycje, których nie udało się przetworzyć (albo 1, gdy całe źródło zawiodło) */
    @Column(name = "error_count")
    private int errors;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(length = 4000)
    private String message;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public RunTrigger getTrigger() { return trigger; }
    public void setTrigger(RunTrigger trigger) { this.trigger = trigger; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }
    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
    public int getDuplicates() { return duplicates; }
    public void setDuplicates(int duplicates) { this.duplicates = duplicates; }
    public int getFilteredOut() { return filteredOut; }
    public void setFilteredOut(int filteredOut) { this.filteredOut = filteredOut; }
    public int getErrors() { return errors; }
    public void setErrors(int errors) { this.errors = errors; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

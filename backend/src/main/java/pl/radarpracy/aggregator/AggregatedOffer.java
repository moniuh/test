package pl.radarpracy.aggregator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import pl.radarpracy.common.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadane agregatora dla oferty ({@code JobOffer}): skąd i kiedy pobrana, klucze deduplikacji,
 * wynik analizy i sygnały prompt injection. Encję oferty z rdzenia zostawiamy bez zmian.
 *
 * <p>Wiersz z {@code sourceId == null} oznacza ofertę dodaną ręcznie, dla której zapamiętano
 * jedynie stan „przejrzana” w skrzynce.</p>
 */
@Entity
@Table(name = "aggregator_offer", indexes = {
        @Index(name = "ix_aggregator_offer_offer", columnList = "offerId", unique = true),
        @Index(name = "ix_aggregator_offer_ext", columnList = "sourceId, externalId"),
        @Index(name = "ix_aggregator_offer_url", columnList = "canonicalUrl"),
        @Index(name = "ix_aggregator_offer_hash", columnList = "contentHash")
})
public class AggregatedOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offerId;

    private Long sourceId;
    private String sourceName;

    @Column(length = 1000)
    private String externalId;
    /** Adres oferty w postaci znormalizowanej (bez fragmentu i parametrów utm_*). */
    @Column(length = 2000)
    private String canonicalUrl;
    /** Skrót znormalizowanego „tytuł + firma” (deduplikacja). */
    @Column(length = 64)
    private String contentHash;

    private Instant fetchedAt;
    private Instant seenAt;

    @Convert(converter = InjectionSignalListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<InjectionSignal> signals = new ArrayList<>();

    /** Co wykryła automatyczna analiza (dla przejrzystości, pomysł #49). */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> analysisNotes = new ArrayList<>();

    public boolean fromSource() {
        return sourceId != null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public Instant getSeenAt() { return seenAt; }
    public void setSeenAt(Instant seenAt) { this.seenAt = seenAt; }
    public List<InjectionSignal> getSignals() { return signals; }
    public void setSignals(List<InjectionSignal> v) { this.signals = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public List<String> getAnalysisNotes() { return analysisNotes; }
    public void setAnalysisNotes(List<String> v) { this.analysisNotes = v == null ? new ArrayList<>() : new ArrayList<>(v); }
}

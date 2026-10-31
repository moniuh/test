package pl.radarpracy.aggregator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.aggregator.AggregatorDtos.Inbox;
import pl.radarpracy.aggregator.AggregatorDtos.OfferMeta;
import pl.radarpracy.aggregator.AggregatorDtos.OfferSignals;
import pl.radarpracy.aggregator.AggregatorDtos.Preview;
import pl.radarpracy.aggregator.AggregatorDtos.SeenRequest;
import pl.radarpracy.aggregator.AggregatorDtos.SeenResult;
import pl.radarpracy.aggregator.AggregatorDtos.Settings;
import pl.radarpracy.aggregator.AggregatorDtos.SourceRequest;
import pl.radarpracy.aggregator.AggregatorDtos.SourceView;
import pl.radarpracy.aggregator.AggregatorDtos.Summary;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;

import java.time.LocalDate;
import java.util.List;

/** REST agregatora ofert: źródła, przebiegi, skrzynka, wyszukiwarka, archiwizacja, sygnały. */
@RestController
@RequestMapping("/api/aggregator")
public class AggregatorController {

    private final AggregatorService aggregator;
    private final InboxService inbox;
    private final SearchService search;
    private final ArchiveService archive;
    private final AggregatedOfferRepository meta;
    private final OfferService offers;
    private final AggregatorProperties props;

    public AggregatorController(AggregatorService aggregator, InboxService inbox, SearchService search,
                                ArchiveService archive, AggregatedOfferRepository meta, OfferService offers,
                                AggregatorProperties props) {
        this.aggregator = aggregator;
        this.inbox = inbox;
        this.search = search;
        this.archive = archive;
        this.meta = meta;
        this.offers = offers;
        this.props = props;
    }

    // --- źródła (pomysły #9, #15, #44, #60) ---

    @GetMapping("/sources")
    List<SourceView> sources() {
        return aggregator.views();
    }

    @GetMapping("/sources/{id}")
    SourceView source(@PathVariable Long id) {
        return aggregator.view(id);
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    SourceView create(@RequestBody SourceRequest request) {
        return aggregator.view(aggregator.create(request).getId());
    }

    @PutMapping("/sources/{id}")
    SourceView update(@PathVariable Long id, @RequestBody SourceRequest request) {
        aggregator.update(id, request);
        return aggregator.view(id);
    }

    record EnabledRequest(boolean enabled) {
    }

    @PostMapping("/sources/{id}/enabled")
    SourceView enabled(@PathVariable Long id, @RequestBody EnabledRequest request) {
        aggregator.setEnabled(id, request.enabled());
        return aggregator.view(id);
    }

    @DeleteMapping("/sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        aggregator.delete(id);
    }

    /** Podgląd pierwszych 5 pozycji źródła bez zapisu. */
    @PostMapping("/sources/test")
    Preview test(@RequestBody SourceRequest request) {
        return aggregator.preview(request);
    }

    @PostMapping("/sources/{id}/run")
    AggregatorRun run(@PathVariable Long id) {
        return aggregator.run(id, RunTrigger.MANUAL);
    }

    @PostMapping("/run-all")
    List<AggregatorRun> runAll() {
        return aggregator.runAll(RunTrigger.MANUAL);
    }

    @GetMapping("/runs")
    List<AggregatorRun> runs(@RequestParam(required = false) Long sourceId,
                             @RequestParam(defaultValue = "50") int limit) {
        return aggregator.history(sourceId, limit);
    }

    @GetMapping("/settings")
    Settings settings() {
        return new Settings(props.userAgent(), props.perHostDelayMs(), props.requestTimeoutSeconds(),
                props.maxItemsPerFetch(), props.schedulerEnabled(), props.intervalMinutes(), props.archiveAfterDays(),
                props.maxResponseBytes() / 1024, List.of("demo-feed.xml", "demo-careers.html"));
    }

    // --- skrzynka (pomysł #48) ---

    @GetMapping("/inbox")
    Inbox inbox(@RequestParam(required = false) Boolean onlyUnseen, @RequestParam(required = false) Long sourceId,
                @RequestParam(required = false) String origin, @RequestParam(required = false) Integer minScore,
                @RequestParam(required = false) String sort) {
        return inbox.inbox(onlyUnseen, sourceId, origin, minScore, sort);
    }

    @PostMapping("/inbox/seen")
    SeenResult seen(@RequestBody SeenRequest request) {
        return inbox.markSeen(request);
    }

    @GetMapping("/summary")
    Summary summary() {
        return inbox.summary();
    }

    // --- wyszukiwarka (pomysł #61) ---

    @GetMapping("/search")
    SearchService.Response search(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(required = false) OfferStatus status,
                                  @RequestParam(defaultValue = "50") int limit) {
        return search.search(q, status, Math.min(limit, 200));
    }

    // --- archiwizacja (pomysł #22) ---

    @PostMapping("/archive-expired")
    ArchiveService.ArchiveReport archiveExpired() {
        return archive.archiveExpired(LocalDate.now());
    }

    // --- metadane i sygnały prompt injection (pomysły #49, #83) ---

    @GetMapping("/offers/{offerId}")
    OfferMeta offerMeta(@PathVariable Long offerId) {
        AggregatedOffer m = meta.findByOfferId(offerId).filter(AggregatedOffer::fromSource)
                .orElseThrow(() -> new NotFoundException("Oferta " + offerId + " nie pochodzi ze źródeł agregatora"));
        JobOffer o = offers.get(offerId);
        return new OfferMeta(offerId, m.getSourceId(), m.getSourceName(), m.getExternalId(), o.getUrl(),
                m.getFetchedAt(), m.getSeenAt(), m.getSignals(), m.getAnalysisNotes());
    }

    /** Sygnały zapisane przy pobraniu; dla ofert dodanych ręcznie — analiza treści na bieżąco. */
    @GetMapping("/offers/{offerId}/signals")
    OfferSignals signals(@PathVariable Long offerId) {
        JobOffer o = offers.get(offerId);
        AggregatedOffer m = meta.findByOfferId(offerId).orElse(null);
        if (m != null && m.fromSource()) {
            return new OfferSignals(offerId, InboxService.ORIGIN_SOURCE, m.getSourceName(), false, m.getSignals());
        }
        return new OfferSignals(offerId, InboxService.ORIGIN_MANUAL, o.getSource(), true,
                InjectionDetector.detect(o.getDescription(), List.of()));
    }
}

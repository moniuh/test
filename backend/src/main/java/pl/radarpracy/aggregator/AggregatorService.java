package pl.radarpracy.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.aggregator.AggregatorDtos.Preview;
import pl.radarpracy.aggregator.AggregatorDtos.PreviewItem;
import pl.radarpracy.aggregator.AggregatorDtos.SourceRequest;
import pl.radarpracy.aggregator.AggregatorDtos.SourceView;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.salary.SalaryNormalizer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Źródła ofert i przebiegi pobierania (pomysły #9, #15, #44, #49, #60): CRUD źródeł, „pobierz teraz”,
 * pobieranie wszystkich (błąd jednego źródła nie zatrzymuje pozostałych), podgląd bez zapisu,
 * deduplikacja i filtry przed zapisem, historia przebiegów.
 */
@Service
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);
    /** Okno deduplikacji po „tytuł + firma”. */
    static final Duration DEDUP_WINDOW = Duration.ofDays(60);
    private static final int PREVIEW_ITEMS = 5;

    enum Outcome { CREATED, DUPLICATE, FILTERED }

    private final AggregatorSourceRepository sources;
    private final AggregatorRunRepository runs;
    private final AggregatedOfferRepository meta;
    private final OfferService offers;
    private final SourceConnector connector;
    private final OfferAnalyzer analyzer;
    private final SalaryNormalizer normalizer;
    private final UrlGuard guard = new UrlGuard();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AggregatorService(AggregatorSourceRepository sources, AggregatorRunRepository runs,
                             AggregatedOfferRepository meta, OfferService offers, SourceConnector connector,
                             OfferAnalyzer analyzer, SalaryNormalizer normalizer) {
        this.sources = sources;
        this.runs = runs;
        this.meta = meta;
        this.offers = offers;
        this.connector = connector;
        this.analyzer = analyzer;
        this.normalizer = normalizer;
    }

    // ------------------------------------------------------------------ źródła

    public List<AggregatorSource> list() {
        return sources.findAllByOrderByIdAsc();
    }

    public AggregatorSource get(Long id) {
        return sources.findById(id).orElseThrow(() -> NotFoundException.of("Źródło", id));
    }

    public List<SourceView> views() {
        Map<Long, long[]> stats = new HashMap<>();
        for (AggregatedOffer m : meta.findBySourceIdIsNotNull()) {
            long[] s = stats.computeIfAbsent(m.getSourceId(), k -> new long[2]);
            s[0]++;
            if (m.getSeenAt() == null) {
                s[1]++;
            }
        }
        List<SourceView> out = new ArrayList<>();
        for (AggregatorSource s : list()) {
            out.add(view(s, stats.getOrDefault(s.getId(), new long[2])));
        }
        return out;
    }

    public SourceView view(Long id) {
        AggregatorSource s = get(id);
        long[] st = new long[2];
        for (AggregatedOffer m : meta.findBySourceIdIsNotNull()) {
            if (id.equals(m.getSourceId())) {
                st[0]++;
                st[1] += m.getSeenAt() == null ? 1 : 0;
            }
        }
        return view(s, st);
    }

    private SourceView view(AggregatorSource s, long[] stats) {
        AggregatorRun last = runs.findBySourceIdOrderByStartedAtDescIdDesc(s.getId(), PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        return new SourceView(s.getId(), s.getName(), s.getType(), s.getUrl(), s.isEnabled(), s.getIncludeKeywords(),
                s.getExcludeKeywords(), s.getLocations(), s.getMinSalary(), s.getLastRunAt(), s.getLastStatus(),
                s.getLastError(), s.getCreatedAt(), s.getType().usesNetwork(), stats[0], stats[1], last);
    }

    @Transactional
    public AggregatorSource create(SourceRequest req) {
        AggregatorSource s = new AggregatorSource();
        apply(req, s);
        return sources.save(s);
    }

    @Transactional
    public AggregatorSource update(Long id, SourceRequest req) {
        AggregatorSource s = get(id);
        apply(req, s);
        return sources.save(s);
    }

    @Transactional
    public AggregatorSource setEnabled(Long id, boolean enabled) {
        AggregatorSource s = get(id);
        s.setEnabled(enabled);
        return sources.save(s);
    }

    /** Usuwa źródło; pobrane oferty zostają (z zapamiętaną nazwą źródła). */
    @Transactional
    public void delete(Long id) {
        sources.delete(get(id));
    }

    private void apply(SourceRequest req, AggregatorSource s) {
        if (req == null) {
            throw new BadRequestException("Brak danych źródła");
        }
        if (Text.isBlank(req.name())) {
            throw new BadRequestException("Podaj nazwę źródła");
        }
        if (req.name().strip().length() > 120) {
            throw new BadRequestException("Nazwa źródła może mieć najwyżej 120 znaków");
        }
        String url = validateLocation(req.type(), req.url());
        validateFilters(req);
        s.setName(req.name().strip());
        s.setType(req.type());
        s.setUrl(url);
        s.setEnabled(req.enabled() == null || req.enabled());
        SourceFilters f = req.filters();
        s.setIncludeKeywords(f.includeKeywords());
        s.setExcludeKeywords(f.excludeKeywords());
        s.setLocations(f.locations());
        s.setMinSalary(f.minSalary());
    }

    /** Walidacja adresu: nazwa pliku dla DEMO, http/https bez adresów lokalnych dla pozostałych. */
    String validateLocation(SourceType type, String url) {
        if (type == null) {
            throw new BadRequestException("Wybierz typ źródła");
        }
        if (type == SourceType.DEMO) {
            return SourceConnector.demoFileName(url);
        }
        if (url != null && url.strip().length() > 2000) {
            throw new BadRequestException("Adres URL jest za długi");
        }
        return guard.checkStatic(url).toString();
    }

    private static void validateFilters(SourceRequest req) {
        for (List<String> list : Arrays.asList(req.includeKeywords(), req.excludeKeywords(), req.locations())) {
            if (list == null) {
                continue;
            }
            if (list.size() > 50) {
                throw new BadRequestException("Filtr może mieć najwyżej 50 pozycji");
            }
            for (String v : list) {
                if (v != null && v.strip().length() > 100) {
                    throw new BadRequestException("Słowo kluczowe lub lokalizacja może mieć najwyżej 100 znaków");
                }
            }
        }
        if (req.minSalary() != null && (req.minSalary() < 0 || req.minSalary() > 1_000_000)) {
            throw new BadRequestException("Minimalne wynagrodzenie musi być liczbą od 0 do 1 000 000 zł");
        }
    }

    // ------------------------------------------------------------------ przebiegi

    public List<AggregatorRun> history(Long sourceId, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
        return sourceId == null ? runs.findAllByOrderByStartedAtDescIdDesc(page)
                : runs.findBySourceIdOrderByStartedAtDescIdDesc(sourceId, page);
    }

    /** Pobiera wszystkie włączone źródła; błąd jednego nie zatrzymuje pozostałych. */
    public List<AggregatorRun> runAll(RunTrigger trigger) {
        List<AggregatorRun> out = new ArrayList<>();
        for (AggregatorSource s : sources.findByEnabledTrueOrderByIdAsc()) {
            try {
                out.add(run(s.getId(), trigger));
            } catch (RuntimeException e) {
                // np. źródło właśnie pobierane w innym wątku — pomijamy
                log.info("Pominięto źródło {}: {}", s.getName(), e.getMessage());
            }
        }
        return out;
    }

    /** Jeden przebieg pobierania źródła; wynik zapisany w historii i w stanie źródła. */
    public AggregatorRun run(Long sourceId, RunTrigger trigger) {
        AggregatorSource source = get(sourceId);
        if (!running.add(sourceId)) {
            throw new BadRequestException("Źródło „" + source.getName() + "” jest właśnie pobierane — poczekaj na koniec");
        }
        AggregatorRun run = new AggregatorRun();
        run.setSourceId(sourceId);
        run.setSourceName(source.getName());
        run.setTrigger(trigger);
        run.setStartedAt(Instant.now());
        List<String> problems = new ArrayList<>();
        try {
            SourceConnector.FetchOutcome fetched = connector.fetch(source.getType(), source.getUrl());
            run.setFetched(fetched.items().size());
            problems.addAll(fetched.warnings());
            DedupContext ctx = dedupContext();
            List<String> filterReasons = new ArrayList<>();
            for (FeedItem item : fetched.items()) {
                try {
                    Outcome o = process(source, item, ctx, filterReasons);
                    switch (o) {
                        case CREATED -> run.setCreated(run.getCreated() + 1);
                        case DUPLICATE -> run.setDuplicates(run.getDuplicates() + 1);
                        case FILTERED -> run.setFilteredOut(run.getFilteredOut() + 1);
                    }
                } catch (RuntimeException e) {
                    run.setErrors(run.getErrors() + 1);
                    if (problems.size() < 8) {
                        problems.add("„" + Text.truncate(item.title(), 60) + "”: " + e.getMessage());
                    }
                    log.warn("Błąd przetwarzania pozycji {} ze źródła {}", item.externalId(), source.getName(), e);
                }
            }
            if (!filterReasons.isEmpty()) {
                problems.add("Odfiltrowane, np.: " + String.join("; ", filterReasons));
            }
            run.setStatus(run.getErrors() > 0 ? RunStatus.PARTIAL : RunStatus.OK);
            run.setMessage(Text.truncate(summary(run) + (problems.isEmpty() ? "" : " " + String.join(" | ", problems)), 3900));
        } catch (SourceException e) {
            run.setStatus(e.blockedByRobots() ? RunStatus.BLOCKED : RunStatus.ERROR);
            run.setErrors(run.getErrors() + 1);
            run.setMessage(Text.truncate(e.getMessage(), 3900));
        } catch (RuntimeException e) {
            log.error("Nieoczekiwany błąd pobierania źródła {}", source.getName(), e);
            run.setStatus(RunStatus.ERROR);
            run.setErrors(run.getErrors() + 1);
            run.setMessage("Nieoczekiwany błąd: " + Text.truncate(String.valueOf(e.getMessage()), 500));
        } finally {
            running.remove(sourceId);
        }
        run.setFinishedAt(Instant.now());
        AggregatorRun saved = runs.save(run);
        sources.findById(sourceId).ifPresent(s -> {
            s.setLastRunAt(saved.getFinishedAt());
            s.setLastStatus(saved.getStatus());
            s.setLastError(saved.getStatus() == RunStatus.OK ? null : Text.truncate(saved.getMessage(), 1990));
            sources.save(s);
        });
        return saved;
    }

    static String summary(AggregatorRun r) {
        return "Pobrano " + r.getFetched() + ": nowe " + r.getCreated() + ", duplikaty " + r.getDuplicates()
                + ", odfiltrowane " + r.getFilteredOut() + ", błędy " + r.getErrors() + ".";
    }

    private Outcome process(AggregatorSource source, FeedItem item, DedupContext ctx, List<String> filterReasons) {
        OfferAnalyzer.Analysis analysis = analyzer.analyze(item, source.getName());
        JobOffer draft = analysis.draft();
        String canonical = DedupKeys.canonicalUrl(draft.getUrl());
        String hash = DedupKeys.contentHash(draft.getTitle(), draft.getCompany());
        if (duplicateReason(source.getId(), item.externalId(), canonical, hash, ctx) != null) {
            return Outcome.DUPLICATE;
        }
        SourceFilter.Decision decision = SourceFilter.evaluate(source.filters(), draft, normalizer);
        if (!decision.accepted()) {
            if (filterReasons.size() < 3) {
                filterReasons.add("„" + Text.truncate(draft.getTitle(), 50) + "” — " + decision.reason());
            }
            return Outcome.FILTERED;
        }
        JobOffer saved = offers.create(draft);
        AggregatedOffer m = new AggregatedOffer();
        m.setOfferId(saved.getId());
        m.setSourceId(source.getId());
        m.setSourceName(source.getName());
        m.setExternalId(item.externalId());
        m.setCanonicalUrl(canonical);
        m.setContentHash(hash);
        m.setFetchedAt(Instant.now());
        m.setSignals(analysis.signals());
        m.setAnalysisNotes(analysis.notes());
        meta.save(m);
        ctx.remember(source.getId(), item.externalId(), canonical, hash);
        return Outcome.CREATED;
    }

    // ------------------------------------------------------------------ deduplikacja

    /** Klucze ofert już obecnych w bazie (wszystkie oferty, także dodane ręcznie) + bieżący przebieg. */
    static final class DedupContext {
        final Set<String> urls = new HashSet<>();
        final Set<String> recentHashes = new HashSet<>();
        final Set<String> batchExternalIds = new HashSet<>();

        void remember(Long sourceId, String externalId, String url, String hash) {
            if (url != null) {
                urls.add(url);
            }
            recentHashes.add(hash);
            batchExternalIds.add(sourceId + "|" + externalId);
        }
    }

    DedupContext dedupContext() {
        DedupContext ctx = new DedupContext();
        Instant since = Instant.now().minus(DEDUP_WINDOW);
        for (JobOffer o : offers.all()) {
            String url = DedupKeys.canonicalUrl(o.getUrl());
            if (url != null) {
                ctx.urls.add(url);
            }
            if (o.getCreatedAt() != null && o.getCreatedAt().isAfter(since)) {
                ctx.recentHashes.add(DedupKeys.contentHash(o.getTitle(), o.getCompany()));
            }
        }
        return ctx;
    }

    /**
     * Powód uznania za duplikat albo null: ten sam externalId w źródle (także dla ofert usuniętych przez
     * kandydata — usunięta nie wraca), ten sam adres URL, albo ten sam „tytuł + firma” w ostatnich 60 dniach.
     */
    String duplicateReason(Long sourceId, String externalId, String canonicalUrl, String hash, DedupContext ctx) {
        if (externalId != null && (ctx.batchExternalIds.contains(sourceId + "|" + externalId)
                || (sourceId != null && meta.existsBySourceIdAndExternalId(sourceId, externalId)))) {
            return "ten sam identyfikator w źródle";
        }
        if (canonicalUrl != null && (ctx.urls.contains(canonicalUrl) || meta.existsByCanonicalUrl(canonicalUrl))) {
            return "ten sam adres URL";
        }
        if (ctx.recentHashes.contains(hash)
                || meta.existsByContentHashAndFetchedAtAfter(hash, Instant.now().minus(DEDUP_WINDOW))) {
            return "to samo stanowisko i firma w ostatnich 60 dniach";
        }
        return null;
    }

    // ------------------------------------------------------------------ podgląd

    /** Test źródła: pierwsze pozycje po analizie i filtrach, bez zapisu. */
    public Preview preview(SourceRequest req) {
        if (req == null) {
            throw new BadRequestException("Brak danych źródła");
        }
        String url = validateLocation(req.type(), req.url());
        validateFilters(req);
        SourceConnector.FetchOutcome fetched = connector.fetch(req.type(), url);
        SourceFilters filters = req.filters();
        DedupContext ctx = dedupContext();
        List<PreviewItem> items = new ArrayList<>();
        for (FeedItem item : fetched.items().subList(0, Math.min(PREVIEW_ITEMS, fetched.items().size()))) {
            OfferAnalyzer.Analysis a = analyzer.analyze(item, Text.isBlank(req.name()) ? "podgląd" : req.name());
            JobOffer d = a.draft();
            SourceFilter.Decision decision = SourceFilter.evaluate(filters, d, normalizer);
            String dup = duplicateReason(null, null, DedupKeys.canonicalUrl(d.getUrl()),
                    DedupKeys.contentHash(d.getTitle(), d.getCompany()), ctx);
            items.add(new PreviewItem(item.externalId(), d.getTitle(), d.getCompany(), d.getLocation(), d.getUrl(),
                    d.getPublishedAt(), d.getExpiresAt(), d.getWorkMode(), d.getContractTypes(), d.getSeniority(),
                    d.getSalaryMin(), d.getSalaryMax(), d.getSalaryCurrency(), d.getSalaryPeriod(), d.getSalaryGross(),
                    d.getRequirements().size(), a.notes(), a.signals(), decision.accepted(), decision.reason(), dup,
                    Text.truncate(d.getDescription(), 220)));
        }
        return new Preview(req.type(), fetched.format(), fetched.title(), fetched.items().size(), items,
                fetched.warnings());
    }
}

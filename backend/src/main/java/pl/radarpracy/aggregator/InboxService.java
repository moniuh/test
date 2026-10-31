package pl.radarpracy.aggregator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.aggregator.AggregatorDtos.Inbox;
import pl.radarpracy.aggregator.AggregatorDtos.InboxItem;
import pl.radarpracy.aggregator.AggregatorDtos.SeenRequest;
import pl.radarpracy.aggregator.AggregatorDtos.SeenResult;
import pl.radarpracy.aggregator.AggregatorDtos.Summary;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.matching.ranking.Exclusion;
import pl.radarpracy.matching.ranking.RankedOffer;
import pl.radarpracy.matching.ranking.RankingService;
import pl.radarpracy.matching.ranking.RankingSnapshot;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.analysis.ScamAssessment;
import pl.radarpracy.offer.analysis.ScamDetector;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Skrzynka nowych ofert (pomysł #48): jeden strumień ofert NEW — pobranych ze źródeł i dodanych ręcznie —
 * z wynikiem dopasowania ({@link MatchService#matchAll}, liczony raz na żądanie), pozycją w rankingu
 * i wykluczeniami z modułu rankingu ({@link RankingService}), źródłem, widełkami,
 * sygnałami prompt injection i stanem „przejrzana”. Oferty dodane ręcznie uznajemy za przejrzane
 * (kandydat sam je dodał).
 */
@Service
public class InboxService {

    public static final String ORIGIN_SOURCE = "SOURCE";
    public static final String ORIGIN_MANUAL = "MANUAL";

    private final OfferService offers;
    private final AggregatedOfferRepository meta;
    private final AggregatorSourceRepository sources;
    private final MatchService matching;
    private final SalaryNormalizer normalizer;
    private final AggregatorProperties props;
    private final RankingService ranking;

    public InboxService(OfferService offers, AggregatedOfferRepository meta, AggregatorSourceRepository sources,
                        MatchService matching, SalaryNormalizer normalizer, AggregatorProperties props,
                        RankingService ranking) {
        this.ranking = ranking;
        this.offers = offers;
        this.meta = meta;
        this.sources = sources;
        this.matching = matching;
        this.normalizer = normalizer;
        this.props = props;
    }

    /**
     * @param onlyUnseen tylko nieprzejrzane
     * @param sourceId   tylko z tego źródła
     * @param origin     SOURCE / MANUAL (opcjonalnie)
     * @param minScore   minimalny wynik dopasowania
     * @param sort       „rank” (domyślnie: ranking dopasowania i preferencji), „match” albo „fresh”
     */
    public Inbox inbox(Boolean onlyUnseen, Long sourceId, String origin, Integer minScore, String sort) {
        Map<Long, AggregatedOffer> metaByOffer = metaByOffer();
        List<JobOffer> candidates = offers.byStatus(OfferStatus.NEW);
        RankingSnapshot ranks = ranking.snapshot(null);
        Map<Long, MatchResult> scores = matching.matchAll(null);
        long unseen = 0;
        List<InboxItem> items = new ArrayList<>();
        for (JobOffer o : candidates) {
            AggregatedOffer m = metaByOffer.get(o.getId());
            boolean fromSource = m != null && m.fromSource();
            boolean seen = !fromSource || m.getSeenAt() != null;
            if (!seen) {
                unseen++;
            }
            if (Boolean.TRUE.equals(onlyUnseen) && seen) {
                continue;
            }
            if (sourceId != null && (!fromSource || !sourceId.equals(m.getSourceId()))) {
                continue;
            }
            if (origin != null && !origin.isBlank()
                    && !origin.equalsIgnoreCase(fromSource ? ORIGIN_SOURCE : ORIGIN_MANUAL)) {
                continue;
            }
            MatchResult match = scores.get(o.getId());
            Integer score = match == null ? null : match.score();
            if (minScore != null && minScore > 0 && (score == null || score < minScore)) {
                continue;
            }
            items.add(item(o, m, fromSource, seen, match, ranks.get(o.getId()).orElse(null)));
        }
        Comparator<InboxItem> fresh = Comparator.comparing(InboxService::freshness,
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<InboxItem> byScore = Comparator.comparing((InboxItem i) -> i.score() == null ? -1 : i.score())
                .reversed();
        // wysokie ryzyko oszustwa (#19) i wykluczenia z preferencji (#32) zawsze na końcu
        Comparator<InboxItem> byRank = Comparator.comparing((InboxItem i) -> "HIGH".equals(i.scamRisk()))
                .thenComparing(InboxItem::excluded)
                .thenComparing(Comparator.comparing((InboxItem i) -> i.rankScore() == null ? -1 : i.rankScore()).reversed());
        if ("fresh".equalsIgnoreCase(sort)) {
            items.sort(fresh.thenComparing(byScore));
        } else if ("match".equalsIgnoreCase(sort)) {
            items.sort(byScore.thenComparing(fresh));
        } else {
            items.sort(byRank.thenComparing(byScore).thenComparing(fresh));
        }
        return new Inbox(items, items.size(), unseen);
    }

    private InboxItem item(JobOffer o, AggregatedOffer m, boolean fromSource, boolean seen, MatchResult match,
                           RankedOffer ranked) {
        NormalizedSalary ns = normalizer.normalize(o);
        ScamAssessment scam = ScamDetector.assess(o);
        List<InjectionSignal> signals = m != null && m.getSignals() != null ? m.getSignals() : List.of();
        return new InboxItem(o.getId(), o.getTitle(), o.getCompany(), o.getLocation(), o.getUrl(), o.getWorkMode(),
                o.getContractTypes(), o.getSeniority(), o.getSalaryMin(), o.getSalaryMax(), o.getSalaryCurrency(),
                o.getSalaryPeriod(), o.getSalaryGross(), ns.comparableMin(), ns.comparableMax(), o.getStatus(),
                fromSource ? ORIGIN_SOURCE : ORIGIN_MANUAL, fromSource ? m.getSourceId() : null,
                fromSource ? m.getSourceName() : Text.isBlank(o.getSource()) ? "dodana ręcznie" : o.getSource(),
                fromSource ? m.getFetchedAt() : o.getCreatedAt(), o.getPublishedAt(), o.getExpiresAt(),
                match == null ? null : match.score(), match == null ? null : match.mustHaveMet(),
                match == null ? null : match.mustHaveTotal(),
                match == null ? List.of() : match.missingSkills().stream().limit(4).toList(), signals, seen,
                Text.truncate(Text.nullToEmpty(o.getDescription()).replaceAll("\\s+", " ").strip(), 240),
                ranked == null ? null : ranked.rankScore(), ranked != null && ranked.excluded(),
                ranked == null ? List.of() : ranked.exclusions().stream().map(Exclusion::reason).toList(),
                scam.level().name(), scam.levelLabel());
    }

    private static Instant freshness(InboxItem i) {
        return i.fetchedAt();
    }

    private Map<Long, AggregatedOffer> metaByOffer() {
        return meta.findAll().stream()
                .collect(Collectors.toMap(AggregatedOffer::getOfferId, Function.identity(), (a, b) -> a));
    }

    /** Oznacza oferty jako przejrzane (wskazane albo wszystkie, opcjonalnie z jednego źródła). */
    @Transactional
    public SeenResult markSeen(SeenRequest req) {
        Instant now = Instant.now();
        int marked = 0;
        if (req != null && Boolean.TRUE.equals(req.all())) {
            List<AggregatedOffer> rows = req.sourceId() == null ? meta.findBySourceIdIsNotNullAndSeenAtIsNull()
                    : meta.findBySourceIdAndSeenAtIsNull(req.sourceId());
            Set<Long> newOffers = offers.byStatus(OfferStatus.NEW).stream().map(JobOffer::getId)
                    .collect(Collectors.toSet());
            for (AggregatedOffer m : rows) {
                if (newOffers.contains(m.getOfferId())) {
                    m.setSeenAt(now);
                    marked++;
                }
            }
            meta.saveAll(rows);
        } else if (req != null && req.offerIds() != null) {
            for (Long id : new HashSet<>(req.offerIds())) {
                if (id == null) {
                    continue;
                }
                AggregatedOffer m = meta.findByOfferId(id).orElse(null);
                if (m != null && m.getSeenAt() == null) {
                    m.setSeenAt(now);
                    meta.save(m);
                    marked++;
                }
            }
        }
        return new SeenResult(marked, unseenCount());
    }

    /** Liczba nieprzejrzanych ofert NEW ze źródeł. */
    public long unseenCount() {
        Set<Long> newOffers = offers.byStatus(OfferStatus.NEW).stream().map(JobOffer::getId).collect(Collectors.toSet());
        return meta.findBySourceIdIsNotNullAndSeenAtIsNull().stream().filter(m -> newOffers.contains(m.getOfferId()))
                .count();
    }

    public Summary summary() {
        List<JobOffer> fresh = offers.byStatus(OfferStatus.NEW);
        Set<Long> newIds = fresh.stream().map(JobOffer::getId).collect(Collectors.toSet());
        Set<Long> existing = offers.all().stream().map(JobOffer::getId).collect(Collectors.toSet());
        long unseen = 0;
        long fromSources = 0;
        long withSignals = 0;
        for (AggregatedOffer m : meta.findBySourceIdIsNotNull()) {
            if (!existing.contains(m.getOfferId())) {
                continue;
            }
            fromSources++;
            if (m.getSignals() != null && !m.getSignals().isEmpty()) {
                withSignals++;
            }
            if (m.getSeenAt() == null && newIds.contains(m.getOfferId())) {
                unseen++;
            }
        }
        List<AggregatorSource> all = sources.findAllByOrderByIdAsc();
        AggregatorSource last = all.stream().filter(s -> s.getLastRunAt() != null)
                .max(Comparator.comparing(AggregatorSource::getLastRunAt)).orElse(null);
        return new Summary(unseen, fresh.size(), all.size(), all.stream().filter(AggregatorSource::isEnabled).count(),
                fromSources, withSignals, last == null ? null : last.getLastRunAt(),
                last == null ? null : last.getLastStatus(), props.schedulerEnabled(), props.intervalMinutes());
    }
}

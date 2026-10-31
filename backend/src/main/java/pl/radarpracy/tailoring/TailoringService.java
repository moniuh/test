package pl.radarpracy.tailoring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.tailoring.TailoringDtos.ApplicationRef;
import pl.radarpracy.tailoring.TailoringDtos.CheckRequest;
import pl.radarpracy.tailoring.TailoringDtos.CheckResult;
import pl.radarpracy.tailoring.TailoringDtos.DeleteResult;
import pl.radarpracy.tailoring.TailoringDtos.Detail;
import pl.radarpracy.tailoring.TailoringDtos.LibraryItem;
import pl.radarpracy.tailoring.TailoringDtos.OfferOption;
import pl.radarpracy.tailoring.TailoringDtos.OfferRef;
import pl.radarpracy.tailoring.TailoringDtos.Preview;
import pl.radarpracy.tailoring.TailoringDtos.PreviewRequest;
import pl.radarpracy.tailoring.TailoringDtos.SaveRequest;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tailoring CV pod ofertę: podgląd (#67, #70), strażnik (#68), wynik przed/po (#78), biblioteka (#94)
 * i eksport DOCX.
 */
@Service
public class TailoringService {

    /** Prefiks pola {@code origin} wersji CV utworzonych przez tailoring (rozpoznaje go strona profilu). */
    public static final String ORIGIN_PREFIX = "dopasowane";

    private final OfferService offers;
    private final ProfileService profiles;
    private final MatchService matching;
    private final ApplicationService applications;
    private final CvTailor tailor;
    private final TruthGuard guard;
    private final AiTailor ai;
    private final DocxExporter docx;
    private final TailoredCvRepository repo;

    public TailoringService(OfferService offers, ProfileService profiles, MatchService matching,
                            ApplicationService applications, CvTailor tailor, TruthGuard guard, AiTailor ai,
                            DocxExporter docx, TailoredCvRepository repo) {
        this.offers = offers;
        this.profiles = profiles;
        this.matching = matching;
        this.applications = applications;
        this.tailor = tailor;
        this.guard = guard;
        this.ai = ai;
        this.docx = docx;
        this.repo = repo;
    }

    // --- podgląd i zapis -------------------------------------------------------------

    public Preview preview(PreviewRequest req) {
        if (req == null || req.offerId() == null) {
            throw new BadRequestException("Wybierz ofertę, do której dopasować CV");
        }
        return build(offers.get(req.offerId()), baseCv(req.baseCvId()), profiles.profile(),
                TailoringOptions.orDefaults(req.options()));
    }

    private Preview build(JobOffer offer, CvVariant base, CandidateProfile profile, TailoringOptions opts) {
        String baseText = Text.nullToEmpty(base.getContent());
        TruthGuard.Facts facts = guard.facts(baseText, profile);
        TailorDraft offline = tailor.tailor(baseText, offer.getRequirements(), profile, opts);
        TailorDraft draft = offline;
        String source = "offline";
        String aiNote = null;
        List<AiTailor.Rejection> rejected = List.of();
        GuardReport report;
        if (opts.ai()) {
            AiTailor.Result r = ai.improve(offline, offer, profile, facts);
            draft = r.draft();
            source = r.source();
            aiNote = r.note();
            rejected = r.rejected();
            report = guard.check(facts, draft.text());
            if (!report.passed() && "ai".equals(source)) {
                // bezpiecznik: wersja AI musi przejść pełną kontrolę — inaczej wracamy do offline
                draft = offline;
                source = "offline";
                aiNote = "Wersja AI nie przeszła pełnej kontroli strażnika — pokazujemy wersję offline.";
                report = guard.check(facts, draft.text());
            }
        } else {
            report = guard.check(facts, draft.text());
        }
        String content = draft.text();
        MatchResult before = matching.match(offer, base, profile);
        MatchResult after = matching.matchRequirements(offer.getRequirements(), content, profile);
        return new Preview(offer.getId(), offer.getTitle(), offer.getCompany(), base.getId(), base.getName(),
                suggestedName(offer), content, source, ai.available(), aiNote, rejected, report,
                ScoreComparison.of(before, after), LineDiff.diff(baseText, content), draft.competences(),
                draft.addedFromProfile(), draft.roles(), opts);
    }

    /** Kontrola tekstu edytowanego przez kandydata — ta sama, którą przechodzi zapis. */
    public CheckResult check(CheckRequest req) {
        if (req == null || req.offerId() == null) {
            throw new BadRequestException("Wybierz ofertę, do której dopasować CV");
        }
        JobOffer offer = offers.get(req.offerId());
        CvVariant base = baseCv(req.baseCvId());
        CandidateProfile profile = profiles.profile();
        String baseText = Text.nullToEmpty(base.getContent());
        String content = Text.nullToEmpty(req.content()).replace("\r\n", "\n");
        return new CheckResult(guard.check(baseText, profile, content),
                ScoreComparison.of(matching.match(offer, base, profile),
                        matching.matchRequirements(offer.getRequirements(), content, profile)),
                LineDiff.diff(baseText, content));
    }

    @Transactional
    public Detail save(SaveRequest req) {
        if (req == null || req.offerId() == null) {
            throw new BadRequestException("Wybierz ofertę, do której dopasować CV");
        }
        JobOffer offer = offers.get(req.offerId());
        CvVariant base = baseCv(req.baseCvId());
        CandidateProfile profile = profiles.profile();
        TailoringOptions opts = TailoringOptions.orDefaults(req.options());
        String content;
        String source;
        boolean edited;
        if (Text.isBlank(req.content())) {
            Preview p = build(offer, base, profile, opts);
            content = p.content();
            source = p.source();
            edited = false;
        } else {
            content = req.content().replace("\r\n", "\n");
            source = "ai".equals(req.source()) ? "ai" : "offline";
            edited = Boolean.TRUE.equals(req.edited());
        }
        // strażnik zawsze sprawdza dokładnie to, co zapisujemy (także tekst edytowany ręcznie)
        GuardReport report = guard.check(Text.nullToEmpty(base.getContent()), profile, content);
        boolean accept = Boolean.TRUE.equals(req.acceptViolations());
        if (!report.passed() && !accept) {
            throw new GuardRejectedException(report);
        }
        int before = matching.match(offer, base, profile).score();
        int after = matching.matchRequirements(offer.getRequirements(), content, profile).score();

        CvVariant cv = new CvVariant();
        cv.setName(Text.isBlank(req.name()) ? suggestedName(offer) : Text.truncate(req.name().strip(), 200));
        cv.setTargetRole(Text.truncate(offer.getTitle(), 250));
        cv.setContent(content);
        cv.setOrigin(ORIGIN_PREFIX + " do oferty #" + offer.getId());
        CvVariant saved = profiles.createCv(cv);
        if (Boolean.TRUE.equals(req.activate())) {
            profiles.activateCv(saved.getId());
        }

        TailoredCv t = new TailoredCv();
        t.setCvVariantId(saved.getId());
        t.setBaseCvId(base.getId());
        t.setBaseCvName(base.getName());
        t.setOfferId(offer.getId());
        t.setOfferTitle(Text.truncate(offer.getTitle(), 250));
        t.setOfferCompany(Text.truncate(offer.getCompany(), 250));
        t.setScoreBefore(before);
        t.setScoreAfter(after);
        t.setGuardReport(report);
        t.setSource(source);
        t.setEdited(edited);
        t.setAcceptedViolations(!report.passed() && accept);
        t.setOptions(opts);
        repo.save(t);
        return detail(saved.getId());
    }

    // --- biblioteka (#94) ------------------------------------------------------------

    public List<LibraryItem> list(Long offerId) {
        List<TailoredCv> records = offerId == null ? repo.findAllByOrderByCreatedAtDescIdDesc()
                : repo.findByOfferIdOrderByCreatedAtDescIdDesc(offerId);
        Context ctx = context();
        return records.stream().map(t -> item(t, ctx)).toList();
    }

    public Detail detail(Long cvVariantId) {
        TailoredCv t = record(cvVariantId);
        Context ctx = context();
        LibraryItem item = item(t, ctx);
        CvVariant cv = ctx.cvs().get(t.getCvVariantId());
        CvVariant base = t.getBaseCvId() == null ? null : ctx.cvs().get(t.getBaseCvId());
        JobOffer offer = ctx.offers().get(t.getOfferId());
        String content = cv == null ? null : Text.nullToEmpty(cv.getContent());
        GuardReport report = t.getGuardReport();
        ScoreComparison comparison = null;
        List<LineDiff.Entry> diff = List.of();
        if (content != null && base != null) {
            // stan bieżący: wersja mogła zostać zmieniona w profilu po zapisie
            report = guard.check(Text.nullToEmpty(base.getContent()), ctx.profile(), content);
            diff = LineDiff.diff(Text.nullToEmpty(base.getContent()), content);
            if (offer != null) {
                comparison = ScoreComparison.of(matching.match(offer, base, ctx.profile()),
                        matching.matchRequirements(offer.getRequirements(), content, ctx.profile()));
            }
        }
        return new Detail(item, content, report, comparison, diff, t.getOptions());
    }

    /**
     * Usuwa wpis z biblioteki, a wersję CV — tylko gdy nie jest aktywna i nie użyto jej w żadnej aplikacji
     * (aplikacje wskazują wersję po {@code cvVariantId}).
     */
    @Transactional
    public DeleteResult delete(Long cvVariantId) {
        TailoredCv t = record(cvVariantId);
        repo.delete(t);
        boolean exists = profiles.cvVariants().stream().anyMatch(c -> c.getId().equals(cvVariantId));
        if (!exists) {
            return new DeleteResult(false, "Usunięto z biblioteki (wersja CV była już usunięta).");
        }
        if (cvVariantId.equals(profiles.profile().getActiveCvId())) {
            return new DeleteResult(false,
                    "Usunięto z biblioteki. Wersja CV została w profilu, bo jest aktywna — zmień aktywną wersję, "
                            + "aby ją usunąć.");
        }
        boolean used = applications.all().stream().anyMatch(a -> cvVariantId.equals(a.getCvVariantId()));
        if (used) {
            return new DeleteResult(false,
                    "Usunięto z biblioteki. Wersja CV została w profilu, bo wysłano ją w aplikacji.");
        }
        profiles.deleteCv(cvVariantId);
        return new DeleteResult(true, "Usunięto dopasowaną wersję CV.");
    }

    /** Plik DOCX dopasowanej wersji. */
    public Docx docx(Long cvVariantId) {
        TailoredCv t = record(cvVariantId);
        CvVariant cv = profiles.cv(t.getCvVariantId());
        String company = Text.isBlank(t.getOfferCompany()) ? t.getOfferTitle() : t.getOfferCompany();
        return new Docx(docx.export(cv.getContent()), "CV-" + slug(company) + ".docx");
    }

    /** Plik do pobrania. */
    public record Docx(byte[] bytes, String filename) {
    }

    /** Oferty NEW/SAVED do dopasowania, od najlepiej dopasowanej do aktywnego CV. */
    public List<OfferOption> offerOptions() {
        Map<Long, MatchResult> scores = matching.matchAll(null);
        Map<Long, Long> counts = repo.findAll().stream()
                .collect(Collectors.groupingBy(TailoredCv::getOfferId, Collectors.counting()));
        return offers.all().stream()
                .filter(o -> o.getStatus() == OfferStatus.NEW || o.getStatus() == OfferStatus.SAVED)
                .map(o -> new OfferOption(o.getId(), o.getTitle(), o.getCompany(), o.getStatus(),
                        scores.containsKey(o.getId()) ? scores.get(o.getId()).score() : 0,
                        counts.getOrDefault(o.getId(), 0L).intValue()))
                .sorted(Comparator.comparingInt(OfferOption::score).reversed().thenComparing(OfferOption::id))
                .toList();
    }

    // --- pomocnicze -------------------------------------------------------------------

    private record Context(CandidateProfile profile, Map<Long, CvVariant> cvs, Map<Long, JobOffer> offers,
                           Map<Long, List<JobApplication>> applications) {
    }

    private Context context() {
        Map<Long, CvVariant> cvs = profiles.cvVariants().stream()
                .collect(Collectors.toMap(CvVariant::getId, Function.identity()));
        Map<Long, JobOffer> all = offers.all().stream().collect(Collectors.toMap(JobOffer::getId, Function.identity()));
        return new Context(profiles.profile(), cvs, all, new HashMap<>());
    }

    private LibraryItem item(TailoredCv t, Context ctx) {
        CvVariant cv = ctx.cvs().get(t.getCvVariantId());
        JobOffer offer = ctx.offers().get(t.getOfferId());
        OfferRef offerRef = offer != null
                ? new OfferRef(offer.getId(), offer.getTitle(), offer.getCompany(), offer.getStatus(), true)
                : new OfferRef(t.getOfferId(), t.getOfferTitle(), t.getOfferCompany(), null, false);
        List<ApplicationRef> apps = ctx.applications()
                .computeIfAbsent(t.getOfferId(), applications::forOffer).stream()
                .map(a -> new ApplicationRef(a.getId(), a.getStatus(), a.getAppliedAt(), a.getCvVariantId(),
                        a.getCvVariantName(), t.getCvVariantId().equals(a.getCvVariantId())))
                .toList();
        CvVariant base = t.getBaseCvId() == null ? null : ctx.cvs().get(t.getBaseCvId());
        GuardReport report = t.getGuardReport() == null ? GuardReport.empty() : t.getGuardReport();
        return new LibraryItem(t.getId(), t.getCvVariantId(), cv != null ? cv.getName() : "(wersja usunięta)",
                cv != null, t.getCvVariantId().equals(ctx.profile().getActiveCvId()), t.getBaseCvId(),
                base != null ? base.getName() : t.getBaseCvName(), offerRef, t.getScoreBefore(), t.getScoreAfter(),
                t.getSource(), t.isEdited(), t.isAcceptedViolations(), report.violations().size(), t.getCreatedAt(),
                apps);
    }

    private TailoredCv record(Long cvVariantId) {
        return repo.findByCvVariantId(cvVariantId)
                .orElseThrow(() -> NotFoundException.of("Dopasowana wersja CV", cvVariantId));
    }

    private CvVariant baseCv(Long id) {
        return profiles.cvOrActive(id)
                .orElseThrow(() -> new BadRequestException("Najpierw dodaj CV w profilu — nie ma czego dopasować"));
    }

    static String suggestedName(JobOffer offer) {
        String name = "Pod ofertę: " + offer.getTitle() + (Text.isBlank(offer.getCompany()) ? ""
                : " — " + offer.getCompany());
        return Text.truncate(name, 200);
    }

    static String slug(String s) {
        String f = Text.fold(Text.nullToEmpty(s)).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return f.isEmpty() ? "dopasowane" : Text.truncate(f, 60).replace("…", "");
    }
}

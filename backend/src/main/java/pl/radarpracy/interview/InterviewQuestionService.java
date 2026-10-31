package pl.radarpracy.interview;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchLevel;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.matching.RequirementMatch;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generator pytań rekrutacyjnych z oferty (pomysł #53).
 *
 * <p>Kolejność: najpierw luki (brakujące umiejętności i wymagania spełnione częściowo — według
 * {@link MatchService}), potem kluczowe umiejętności techniczne (obowiązkowe przed mile widzianymi),
 * pytania behawioralne powiązane z wymaganiami miękkimi, motywacyjne i o firmę. Wybór jest
 * deterministyczny: te same dane dają te same pytania.</p>
 */
@Service
public class InterviewQuestionService {

    static final int MAX_GAPS = 6;
    static final int MAX_TECHNICAL = 6;
    static final int MAX_BEHAVIORAL = 4;
    static final int MIN_BEHAVIORAL = 2;
    static final int MAX_COMPANY = 2;
    private static final int QUOTE_MAX = 200;
    private static final List<String> PROJECT_WORDS = List.of("o projekcie", "budujemy", "tworzymy", "rozwijamy",
            "projekt", "system", "produkt", "platform", "we build", "we are building");

    private final InterviewQuestionBank bank;
    private final SkillDictionary dictionary;
    private final MatchService matching;
    private final OfferService offers;

    public InterviewQuestionService(InterviewQuestionBank bank, SkillDictionary dictionary, MatchService matching,
                                    OfferService offers) {
        this.bank = bank;
        this.dictionary = dictionary;
        this.matching = matching;
        this.offers = offers;
    }

    public List<InterviewQuestion> forOffer(Long offerId, Long cvId) {
        JobOffer offer = offers.get(offerId);
        return generate(offer, matching.match(offerId, cvId));
    }

    List<InterviewQuestion> generate(JobOffer offer, MatchResult match) {
        Collector out = new Collector();
        boolean hasCv = match.cvId() != null || match.cvName() != null;
        List<RequirementMatch> reqs = match.requirements().stream()
                .sorted(Comparator.comparing((RequirementMatch r) -> !r.mustHave()))
                .toList();
        Set<String> gapSkills = new LinkedHashSet<>();

        // 1. luki
        if (hasCv) {
            int gaps = 0;
            int idx = 0;
            for (String skill : match.missingSkills()) {
                if (gaps >= MAX_GAPS) {
                    break;
                }
                if (isSoftOrEducation(skill)) {
                    continue;
                }
                RequirementMatch r = reqs.stream().filter(x -> x.skills().contains(skill)).findFirst().orElse(null);
                if (!absentFromCv(skill, r)) {
                    continue; // jest w CV, ale np. za mało lat — obsłuży to pytanie o wymaganie częściowe
                }
                InterviewQuestionBank.Template t = pick(bank.gap(), idx++);
                String related = r != null && r.level() == MatchLevel.RELATED && r.evidenceVerified() ? r.evidence() : null;
                String tip = t.tip() + (related != null ? " Pokrewne doświadczenie z CV: „" + related + "”." : "");
                if (out.add(InterviewQuestion.GAP, skill, fill(t.q(), skill, r, offer),
                        "Luka: " + label(r) + (r != null && r.note() != null ? " — " + r.note() : ""), tip,
                        r == null ? null : r.requirement(), related)) {
                    gaps++;
                    gapSkills.add(skill);
                }
            }
            idx = 0;
            for (RequirementMatch r : reqs) {
                if (gaps >= MAX_GAPS) {
                    break;
                }
                if (r.level() != MatchLevel.PARTIAL || (!r.skills().isEmpty() && gapSkills.containsAll(r.skills()))) {
                    continue;
                }
                InterviewQuestionBank.Template t = pick(bank.partial(), idx++);
                String skill = r.skills().stream().filter(s -> !gapSkills.contains(s)).findFirst().orElse(null);
                String evidence = r.evidenceVerified() ? r.evidence() : null;
                if (out.add(InterviewQuestion.GAP, skill, fill(t.q(), skill, r, offer),
                        "Spełniasz częściowo: " + label(r) + (r.note() != null ? " — " + r.note() : ""),
                        t.tip() + (evidence != null ? " Twój najbliższy dowód w CV: „" + evidence + "”." : ""),
                        r.requirement(), evidence)) {
                    gaps++;
                }
            }
        }

        // 2. kluczowe umiejętności (techniczne)
        Set<String> techSkills = new HashSet<>();
        for (RequirementMatch r : reqs) {
            for (String skill : r.skills()) {
                if (techSkills.size() >= MAX_TECHNICAL) {
                    break;
                }
                if (gapSkills.contains(skill) || techSkills.contains(skill) || isSoftOrEducation(skill)) {
                    continue;
                }
                InterviewQuestionBank.Template t = technicalTemplate(skill);
                String evidence = hasCv && r.evidenceVerified() && r.level() != MatchLevel.MISSING ? r.evidence() : null;
                String tip = t.tip() + (evidence != null ? " Oprzyj odpowiedź na przykładzie z CV: „" + evidence + "”." : "");
                if (out.add(InterviewQuestion.TECHNICAL, skill, fill(t.q(), skill, r, offer),
                        (r.mustHave() ? "Wymaganie obowiązkowe: „" : "Mile widziane: „") + r.requirement() + "”", tip,
                        r.requirement(), evidence)) {
                    techSkills.add(skill);
                }
            }
        }

        // 3. behawioralne (wymagania miękkie i wymagania spoza słownika) + pytania o branżę
        List<InterviewQuestion> companyExtra = new ArrayList<>();
        int behavioral = 0;
        int reqIdx = 0;
        for (RequirementMatch r : reqs) {
            if (behavioral >= MAX_BEHAVIORAL) {
                break;
            }
            for (String skill : r.skills()) {
                if (behavioral >= MAX_BEHAVIORAL) {
                    break;
                }
                if (!isSoft(skill)) {
                    continue;
                }
                List<InterviewQuestionBank.Template> soft = bank.forSoftSkill(skill);
                InterviewQuestionBank.Template t = soft.isEmpty() ? pick(bank.requirementBehavioral(), 0) : soft.get(0);
                if (out.add(InterviewQuestion.BEHAVIORAL, skill, fill(t.q(), skill, r, offer),
                        "Wymaganie miękkie: " + label(r), t.tip(), r.requirement(), null)) {
                    behavioral++;
                }
            }
            if (r.skills().isEmpty() && behavioral < MAX_BEHAVIORAL) {
                String folded = Text.fold(r.requirement());
                InterviewQuestionBank.KeywordTemplate k = bank.keywords().stream()
                        .filter(kt -> kt.match().stream().anyMatch(folded::contains)).findFirst().orElse(null);
                if (k != null && InterviewQuestion.COMPANY.equals(k.category())) {
                    companyExtra.add(new InterviewQuestion(fill(k.q(), null, r, offer), InterviewQuestion.COMPANY, null,
                            "Z wymagania: " + label(r), k.tip(), r.requirement(), null));
                } else {
                    String q = k != null ? k.q() : pick(bank.requirementBehavioral(), reqIdx++).q();
                    String tip = k != null ? k.tip() : pick(bank.requirementBehavioral(), 0).tip();
                    if (out.add(InterviewQuestion.BEHAVIORAL, null, fill(q, null, r, offer), "Z wymagania: " + label(r),
                            tip, r.requirement(), null)) {
                        behavioral++;
                    }
                }
            }
        }
        for (InterviewQuestionBank.Template t : bank.behavioral()) {
            if (behavioral >= MIN_BEHAVIORAL) {
                break;
            }
            if (out.add(InterviewQuestion.BEHAVIORAL, null, fill(t.q(), null, null, offer),
                    "Pytanie behawioralne zadawane na większości rozmów", t.tip(), null, null)) {
                behavioral++;
            }
        }

        // 4. motywacyjne
        for (InterviewQuestionBank.Template t : bank.motivational()) {
            out.add(InterviewQuestion.MOTIVATIONAL, null, fill(t.q(), null, null, offer),
                    "Pytanie motywacyjne — " + (Text.isBlank(offer.getCompany()) ? "o tę ofertę"
                            : "o ofertę " + offer.getCompany()), t.tip(), null, null);
        }

        // 5. o firmę
        int company = 0;
        String projectQuote = projectQuote(offer.getDescription());
        for (InterviewQuestionBank.Template t : bank.company()) {
            if (out.add(InterviewQuestion.COMPANY, null, fill(t.q(), null, null, offer),
                    projectQuote != null ? "Ogłoszenie opisuje: „" + projectQuote + "”"
                            : "Rekruterzy sprawdzają, czy znasz firmę i jej produkt", t.tip(), null, null)) {
                company++;
            }
        }
        for (InterviewQuestion q : companyExtra) {
            if (company >= MAX_COMPANY) {
                break;
            }
            if (out.add(q.category(), q.skill(), q.question(), q.why(), q.tip(), q.requirement(), q.evidence())) {
                company++;
            }
        }
        return out.list;
    }

    /** Czy umiejętności naprawdę brak w CV (a nie np. jest, ale z za małą liczbą lat). */
    static boolean absentFromCv(String skill, RequirementMatch r) {
        if (r == null || r.level() == MatchLevel.MISSING || r.level() == MatchLevel.RELATED) {
            return true;
        }
        return r.level() == MatchLevel.PARTIAL && r.note() != null && r.note().contains("Brak " + skill);
    }

    private InterviewQuestionBank.Template technicalTemplate(String skill) {
        List<InterviewQuestionBank.Template> own = bank.forSkill(skill);
        if (!own.isEmpty()) {
            return own.get(0);
        }
        String category = dictionary.get(skill).map(Skill::category).orElse(null);
        List<InterviewQuestionBank.Template> byCategory = bank.forCategory(category);
        return byCategory.isEmpty() ? bank.fallbackTechnical().get(0) : byCategory.get(0);
    }

    /** Dosłowny fragment ogłoszenia o projekcie/produkcie (np. „Budujemy platformę płatności…”). */
    static String projectQuote(String description) {
        if (Text.isBlank(description)) {
            return null;
        }
        String folded = Text.fold(description);
        for (String word : PROJECT_WORDS) {
            int idx = folded.indexOf(word);
            while (idx >= 0) {
                int lineStart = description.lastIndexOf('\n', idx) + 1;
                String linePrefix = description.substring(lineStart, idx).strip();
                // pomijamy punkty listy wymagań
                if (!linePrefix.startsWith("-") && !linePrefix.startsWith("•")) {
                    String quote = Text.quoteAround(description, idx, idx + word.length(), QUOTE_MAX);
                    if (quote.length() >= 20 && description.contains(quote)) {
                        return quote;
                    }
                }
                idx = folded.indexOf(word, idx + 1);
            }
        }
        return null;
    }

    private boolean isSoft(String skill) {
        return dictionary.get(skill).map(s -> "miekkie".equals(s.category())).orElse(false);
    }

    private boolean isSoftOrEducation(String skill) {
        return dictionary.get(skill).map(s -> "miekkie".equals(s.category()) || "wyksztalcenie".equals(s.category()))
                .orElse(false);
    }

    private static InterviewQuestionBank.Template pick(List<InterviewQuestionBank.Template> list, int index) {
        return list.get(Math.floorMod(index, list.size()));
    }

    private static String label(RequirementMatch r) {
        if (r == null) {
            return "wymaganie oferty";
        }
        return "„" + r.requirement() + "” (" + (r.mustHave() ? "obowiązkowe" : "mile widziane") + ")";
    }

    private static String fill(String template, String skill, RequirementMatch r, JobOffer offer) {
        return template
                .replace("{skill}", skill == null ? "tej umiejętności" : skill)
                .replace("{requirement}", r == null ? "" : r.requirement())
                .replace("{note}", r == null || r.note() == null ? "" : r.note())
                .replace("{company}", Text.isBlank(offer.getCompany()) ? "naszej firmie" : offer.getCompany())
                .replace("{title}", Text.isBlank(offer.getTitle()) ? "na tym stanowisku" : offer.getTitle());
    }

    /** Lista pytań bez powtórzeń treści. */
    private static final class Collector {
        final List<InterviewQuestion> list = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        boolean add(String category, String skill, String question, String why, String tip, String requirement,
                    String evidence) {
            if (!seen.add(question)) {
                return false;
            }
            list.add(new InterviewQuestion(question, category, skill, why, tip, requirement, evidence));
            return true;
        }
    }
}

package pl.radarpracy.matching;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.YearsParser;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.skills.SkillHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dopasowanie oferty do CV wymaganie po wymaganiu (MVP + pomysły #27, #28, #34).
 *
 * <p>Zasady: każdy dowód to dosłowny cytat z CV (sprawdzany w kodzie), wynik liczy kod
 * deterministycznie (wagi: obowiązkowe 3, mile widziane 1), a każda strata punktów ma
 * wyjaśnienie zrozumiałe dla kandydata.</p>
 */
@Service
public class MatchService {

    static final int MUST_WEIGHT = 3;
    static final int NICE_WEIGHT = 1;
    private static final int QUOTE_MAX = 220;

    private final SkillDictionary dictionary;
    private final ProfileService profiles;
    private final OfferService offers;

    public MatchService(SkillDictionary dictionary, ProfileService profiles, OfferService offers) {
        this.dictionary = dictionary;
        this.profiles = profiles;
        this.offers = offers;
    }

    /** Dopasowanie oferty do wskazanej wersji CV (albo aktywnej, gdy cvId == null). */
    public MatchResult match(Long offerId, Long cvId) {
        JobOffer offer = offers.get(offerId);
        CvVariant cv = profiles.cvOrActive(cvId).orElse(null);
        return match(offer, cv, profiles.profile());
    }

    /** Wyniki dla wszystkich ofert względem jednej wersji CV. */
    public Map<Long, MatchResult> matchAll(Long cvId) {
        CvVariant cv = profiles.cvOrActive(cvId).orElse(null);
        CandidateProfile profile = profiles.profile();
        CandidateSkills candidate = analyze(cv, profile);
        Map<Long, MatchResult> out = new LinkedHashMap<>();
        for (JobOffer o : offers.all()) {
            out.put(o.getId(), match(o, cv, candidate));
        }
        return out;
    }

    public MatchResult match(JobOffer offer, CvVariant cv, CandidateProfile profile) {
        return match(offer, cv, analyze(cv, profile));
    }

    /** Dopasowanie dowolnych wymagań do dowolnego tekstu CV (np. podgląd przed zapisem). */
    public MatchResult matchRequirements(List<Requirement> requirements, String cvText, CandidateProfile profile) {
        CvVariant cv = new CvVariant();
        cv.setName("podgląd");
        cv.setContent(cvText);
        JobOffer offer = new JobOffer();
        offer.setRequirements(requirements);
        return match(offer, cv, analyze(cv, profile));
    }

    // ------------------------------------------------------------------

    /** Umiejętności kandydata: z CV (z cytatem) i z profilu (z latami). */
    record CandidateSkills(String cvText, Long cvId, String cvName, Map<String, SkillHit> inCv,
                           Map<String, List<SkillHit>> allHits, Map<String, SkillEntry> inProfile) {
    }

    CandidateSkills analyze(CvVariant cv, CandidateProfile profile) {
        String text = cv == null ? "" : Text.nullToEmpty(cv.getContent());
        Map<String, SkillHit> inCv = new LinkedHashMap<>();
        Map<String, List<SkillHit>> allHits = new HashMap<>();
        for (SkillHit h : dictionary.find(text)) {
            inCv.putIfAbsent(h.skill(), h);
            allHits.computeIfAbsent(h.skill(), k -> new ArrayList<>()).add(h);
        }
        // relacja „obejmuje”: Spring Boot w CV ⇒ Spring i Java (dowodem jest wzmianka o Spring Boot)
        for (SkillHit h : new ArrayList<>(inCv.values())) {
            for (String implied : dictionary.impliedBy(h.skill())) {
                inCv.putIfAbsent(implied, h);
                allHits.computeIfAbsent(implied, k -> new ArrayList<>()).add(h);
            }
        }
        Map<String, SkillEntry> inProfile = new HashMap<>();
        if (profile != null) {
            for (SkillEntry s : profile.getSkills()) {
                inProfile.put(dictionary.canonicalName(s.name()), s);
            }
        }
        return new CandidateSkills(text, cv == null ? null : cv.getId(), cv == null ? null : cv.getName(), inCv,
                allHits, inProfile);
    }

    MatchResult match(JobOffer offer, CvVariant cv, CandidateSkills candidate) {
        List<Requirement> reqs = offer.getRequirements() == null ? List.of() : offer.getRequirements();
        int totalWeight = reqs.stream().mapToInt(r -> r.mustHave() ? MUST_WEIGHT : NICE_WEIGHT).sum();
        List<RequirementMatch> items = new ArrayList<>();
        Set<String> matched = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        double sum = 0;
        int mustMet = 0;
        int mustTotal = 0;
        int niceMet = 0;
        int niceTotal = 0;
        for (Requirement r : reqs) {
            int weight = r.mustHave() ? MUST_WEIGHT : NICE_WEIGHT;
            double maxPoints = totalWeight == 0 ? 0 : 100.0 * weight / totalWeight;
            RequirementMatch m = matchOne(r, weight, maxPoints, candidate, matched, missing);
            items.add(m);
            sum += m.credit() * weight;
            if (r.mustHave()) {
                mustTotal++;
                mustMet += m.met() ? 1 : 0;
            } else {
                niceTotal++;
                niceMet += m.met() ? 1 : 0;
            }
        }
        missing.removeAll(matched);
        int score = totalWeight == 0 ? 0 : (int) Math.round(100.0 * sum / totalWeight);
        String summary = summary(reqs.isEmpty(), cv == null, mustMet, mustTotal, niceMet, niceTotal, score);
        return new MatchResult(offer.getId(), candidate.cvId(), candidate.cvName(), score, mustMet, mustTotal, niceMet,
                niceTotal, items, List.copyOf(matched), List.copyOf(missing), summary, explanation(items));
    }

    private RequirementMatch matchOne(Requirement r, int weight, double maxPoints, CandidateSkills c,
                                      Set<String> matched, Set<String> missing) {
        Integer minYears = r.minYears() != null ? r.minYears() : YearsParser.requiredYears(r.text());
        List<String> skills = new ArrayList<>(dictionary.skillsIn(r.text()));
        if (skills.isEmpty()) {
            return keywordMatch(r, weight, maxPoints, c, minYears);
        }
        String folded = Text.fold(r.text());
        boolean anyOf = folded.contains(" lub ") || folded.contains(" albo ") || folded.contains(" or ")
                || folded.contains("jednego z") || folded.contains("one of") || folded.contains("/");

        List<SkillOutcome> outcomes = new ArrayList<>();
        for (String s : skills) {
            outcomes.add(skillOutcome(s, r.text(), minYears, c));
        }
        SkillOutcome best = outcomes.stream().max(Comparator.comparingDouble(SkillOutcome::credit)).orElseThrow();
        double credit = anyOf ? best.credit()
                : outcomes.stream().mapToDouble(SkillOutcome::credit).average().orElse(0);
        for (SkillOutcome o : outcomes) {
            if (o.credit() >= 0.75 || (anyOf && credit >= 0.75 && o == best)) {
                matched.add(o.skill());
            } else if (!anyOf || credit < 0.75) {
                missing.add(o.skill());
            }
        }
        MatchLevel level;
        if (credit >= 0.999) {
            level = MatchLevel.FULL;
        } else if (credit == 0) {
            level = MatchLevel.MISSING;
        } else if (anyOf || outcomes.size() == 1) {
            // jedna umiejętność (lub „którakolwiek z”) — poziom najlepszego wyniku
            level = best.level() == MatchLevel.FULL ? MatchLevel.PARTIAL : best.level();
        } else {
            level = MatchLevel.PARTIAL;
        }
        // dowód i notatka: z najlepszego wyniku (dla „wszystkich” — także lista braków)
        String evidence = outcomes.stream().filter(o -> o.evidence() != null)
                .max(Comparator.comparingDouble(SkillOutcome::credit)).map(SkillOutcome::evidence).orElse(null);
        List<String> notes = new ArrayList<>();
        for (SkillOutcome o : outcomes) {
            if (o.note() != null && (!anyOf || o == best)) {
                notes.add(o.note());
            }
        }
        if (anyOf && credit == 0) {
            notes.add("Brak w CV: " + String.join(" / ", skills));
        }
        boolean verified = evidence != null && c.cvText().contains(evidence);
        Integer candYears = best.candidateYears();
        return new RequirementMatch(r.text(), r.mustHave(), weight, level, round(credit), round(credit * maxPoints),
                round(maxPoints), skills, verified ? evidence : null, verified,
                notes.isEmpty() ? null : String.join(" ", new LinkedHashSet<>(notes)), minYears, candYears);
    }

    private record SkillOutcome(String skill, MatchLevel level, double credit, String evidence, String note,
                                Integer candidateYears) {
    }

    private SkillOutcome skillOutcome(String skill, String requirementText, Integer minYears, CandidateSkills c) {
        SkillHit hit = c.inCv().get(skill);
        SkillEntry declared = c.inProfile().get(skill);
        MatchLevel level;
        double credit;
        String evidence = null;
        String note = null;
        if (hit != null) {
            level = MatchLevel.FULL;
            credit = 1.0;
            hit = bestHit(c, skill, minYears != null);
            evidence = Text.quoteAround(c.cvText(), hit.start(), hit.end(), QUOTE_MAX);
            if (!hit.skill().equals(skill)) {
                note = skill + " — wynika z: " + hit.skill() + ".";
            }
        } else if (declared != null) {
            level = MatchLevel.PROFILE_ONLY;
            credit = 0.8;
            note = skill + " jest w profilu, ale nie w tej wersji CV — dopisz to do CV.";
        } else {
            String related = relatedInCv(skill, c);
            if (related != null) {
                level = MatchLevel.RELATED;
                credit = 0.4;
                SkillHit rh = c.inCv().get(related);
                evidence = Text.quoteAround(c.cvText(), rh.start(), rh.end(), QUOTE_MAX);
                note = "Brak " + skill + ", ale znasz pokrewną technologię: " + related + ".";
            } else {
                return new SkillOutcome(skill, MatchLevel.MISSING, 0, null, "Brak " + skill + " w CV.", null);
            }
        }

        Integer candidateYears = declared != null && declared.years() != null ? declared.years()
                : evidence != null ? YearsParser.yearsInText(lineOf(c.cvText(), evidence)) : null;

        Skill def = dictionary.get(skill).orElse(null);
        if (def != null && def.isLanguage()) {
            Integer required = LanguageLevel.parse(requirementText);
            Integer has = evidence != null ? LanguageLevel.parse(lineOf(c.cvText(), evidence)) : null;
            if (required != null && has != null && has < required) {
                credit *= Math.max(0.25, 1.0 - 0.25 * (required - has));
                level = MatchLevel.PARTIAL;
                note = join(note, skill + ": wymagany poziom " + LanguageLevel.label(required) + ", w CV "
                        + LanguageLevel.label(has) + ".");
            } else if (required != null && has == null && level == MatchLevel.FULL) {
                credit *= 0.9;
                note = join(note, skill + ": wymagany poziom " + LanguageLevel.label(required)
                        + " — podaj w CV swój poziom.");
            }
            return new SkillOutcome(skill, level, credit, evidence, note, null);
        }

        if (minYears != null && credit > 0) {
            if (candidateYears != null && candidateYears < minYears) {
                credit *= Math.max(0.4, (double) candidateYears / minYears);
                if (level == MatchLevel.FULL) {
                    level = MatchLevel.PARTIAL;
                }
                note = join(note, skill + ": wymagane " + minYears + " " + lata(minYears) + ", masz " + candidateYears
                        + " " + lata(candidateYears) + ".");
            } else if (candidateYears == null) {
                credit *= 0.9;
                note = join(note, skill + ": wymagane " + minYears + " " + lata(minYears)
                        + " — dopisz w CV lub profilu, ile lat pracujesz z tą technologią.");
            }
        }
        return new SkillOutcome(skill, level, credit, evidence, note, candidateYears);
    }

    /**
     * Najlepszy cytat dla umiejętności: przy wymaganych latach — linia z datami/latami (najwięcej lat),
     * w pozostałych przypadkach — najbardziej opisowy fragment (pełne zdanie zamiast nagłówka).
     */
    private static SkillHit bestHit(CandidateSkills c, String skill, boolean preferYears) {
        List<SkillHit> hits = c.allHits().getOrDefault(skill, List.of(c.inCv().get(skill)));
        SkillHit best = hits.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (SkillHit h : hits) {
            String line = lineOf(c.cvText(), c.cvText().substring(h.start(), h.end()), h.start());
            int words = line.split("\\s+").length;
            Integer years = YearsParser.yearsInText(line);
            int score = Math.min(words, 25) + (preferYears && years != null ? 100 + years : 0);
            if (score > bestScore) {
                bestScore = score;
                best = h;
            }
        }
        return best;
    }

    private static String lineOf(String text, String fragment, int at) {
        int from = text.lastIndexOf('\n', at) + 1;
        int to = text.indexOf('\n', at + fragment.length());
        return text.substring(from, to < 0 ? text.length() : to);
    }

    private String relatedInCv(String skill, CandidateSkills c) {
        for (String has : c.inCv().keySet()) {
            if (dictionary.sameFamily(skill, has)) {
                return has;
            }
        }
        return null;
    }

    /**
     * Wymagania bez umiejętności ze słownika (np. „doświadczenie w pracy z klientem biznesowym”):
     * szukamy linii CV o największym pokryciu słów (porównanie rdzeni, odporne na polską odmianę).
     */
    private RequirementMatch keywordMatch(Requirement r, int weight, double maxPoints, CandidateSkills c,
                                          Integer minYears) {
        List<String> reqStems = Text.tokens(r.text(), 4).stream().map(t -> Text.stem(t, 5)).distinct().toList();
        String bestLine = null;
        double bestRatio = 0;
        if (!reqStems.isEmpty()) {
            for (String line : Text.lines(c.cvText())) {
                Set<String> lineStems = new LinkedHashSet<>();
                for (String t : Text.tokens(line, 4)) {
                    lineStems.add(Text.stem(t, 5));
                }
                long common = reqStems.stream().filter(lineStems::contains).count();
                double ratio = (double) common / reqStems.size();
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestLine = line;
                }
            }
        }
        MatchLevel level;
        double credit;
        String note;
        if (bestRatio >= 0.6) {
            level = MatchLevel.FULL;
            credit = 1.0;
            note = "Dopasowanie po słowach kluczowych — sprawdź, czy cytat faktycznie potwierdza wymaganie.";
        } else if (bestRatio >= 0.34) {
            level = MatchLevel.PARTIAL;
            credit = 0.5;
            note = "Częściowe pokrycie słów kluczowych — rozważ doprecyzowanie CV.";
        } else {
            level = MatchLevel.MISSING;
            credit = 0;
            bestLine = null;
            note = "Nie znaleziono w CV fragmentu odpowiadającego temu wymaganiu.";
        }
        Integer candYears = null;
        if (bestLine != null && minYears != null) {
            candYears = YearsParser.yearsInText(bestLine);
            if (candYears != null && candYears < minYears) {
                credit *= Math.max(0.4, (double) candYears / minYears);
                level = MatchLevel.PARTIAL;
                note = join(note, "Wymagane " + minYears + " " + lata(minYears) + ", w CV " + candYears + " "
                        + lata(candYears) + ".");
            }
        }
        String evidence = bestLine == null ? null : Text.quoteAround(c.cvText(), c.cvText().indexOf(bestLine),
                c.cvText().indexOf(bestLine) + bestLine.length(), QUOTE_MAX);
        boolean verified = evidence != null && c.cvText().contains(evidence);
        return new RequirementMatch(r.text(), r.mustHave(), weight, level, round(credit), round(credit * maxPoints),
                round(maxPoints), List.of(), verified ? evidence : null, verified, note, minYears, candYears);
    }

    private static String lineOf(String text, String fragment) {
        int idx = text.indexOf(fragment);
        if (idx < 0) {
            return fragment;
        }
        int from = text.lastIndexOf('\n', idx) + 1;
        int to = text.indexOf('\n', idx + fragment.length());
        return text.substring(from, to < 0 ? text.length() : to);
    }

    private static String summary(boolean noReqs, boolean noCv, int mustMet, int mustTotal, int niceMet, int niceTotal,
                                  int score) {
        if (noCv) {
            return "Dodaj CV w profilu, aby zobaczyć dopasowanie.";
        }
        if (noReqs) {
            return "Oferta nie ma wymagań do porównania — dodaj je ręcznie lub wyodrębnij z treści.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Spełniasz ").append(mustMet).append(" z ").append(mustTotal).append(" wymagań obowiązkowych");
        if (niceTotal > 0) {
            sb.append(" i ").append(niceMet).append(" z ").append(niceTotal).append(" mile widzianych");
        }
        sb.append(". ");
        if (score >= 80) {
            sb.append("Bardzo dobre dopasowanie — warto aplikować.");
        } else if (score >= 60) {
            sb.append("Dobre dopasowanie — uzupełnij CV o brakujące elementy.");
        } else if (score >= 40) {
            sb.append("Częściowe dopasowanie — aplikuj, jeśli braki da się szybko nadrobić.");
        } else {
            sb.append("Słabe dopasowanie.");
        }
        return sb.toString();
    }

    /** Pomysł #34: największe straty punktów, potem największe zyski. */
    private static List<String> explanation(List<RequirementMatch> items) {
        List<String> out = new ArrayList<>();
        items.stream()
                .filter(i -> i.maxPoints() - i.points() >= 0.5)
                .sorted(Comparator.comparingDouble((RequirementMatch i) -> i.maxPoints() - i.points()).reversed())
                .limit(5)
                .forEach(i -> out.add(String.format(Locale.ROOT, "−%.0f pkt: %s (%s) — %s", i.maxPoints() - i.points(),
                        Text.truncate(i.requirement(), 80), i.mustHave() ? "obowiązkowe" : "mile widziane",
                        i.note() == null ? "częściowo spełnione" : i.note())));
        items.stream()
                .filter(i -> i.points() >= 0.5)
                .sorted(Comparator.comparingDouble(RequirementMatch::points).reversed())
                .limit(3)
                .forEach(i -> out.add(String.format(Locale.ROOT, "+%.0f pkt: %s", i.points(),
                        Text.truncate(i.requirement(), 80))));
        return out;
    }

    private static String join(String a, String b) {
        return a == null ? b : a + " " + b;
    }

    private static String lata(int n) {
        if (n == 1) {
            return "rok";
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "lata" : "lat";
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

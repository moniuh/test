package pl.radarpracy.profile.cv;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchLevel;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.matching.RequirementMatch;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.skills.SkillHit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dopasowanie CV do oferty — tailoring (pomysł #5).
 *
 * <p>Na bazie {@link MatchService#match} dzieli brakujące elementy na:</p>
 * <ol>
 *     <li>umiejętności z profilu, których nie ma w tej wersji CV — z gotową linią do sekcji
 *     „Umiejętności” (lata z profilu) i szkicem zdania z miejscami do uzupełnienia w [nawiasach];</li>
 *     <li>pokrewne (ta sama rodzina technologii) — jak uczciwie opisać posiadane doświadczenie;</li>
 *     <li>brakujące całkiem — do nauki albo pominięcia (nigdy nie proponujemy dopisania czegoś,
 *     czego nie ma w profilu ani w CV);</li>
 *     <li>przewidywany wynik po dopisaniu linii z punktu 1 — liczony przez
 *     {@link MatchService#matchRequirements} na CV z dopisanymi liniami.</li>
 * </ol>
 */
@Service
public class CvTailoringService {

    static final int QUOTE_MAX = 200;
    private static final Pattern FEMININE = Pattern.compile("\\p{L}{2,}łam(?![\\p{L}])");
    private static final Pattern MASCULINE = Pattern.compile("\\p{L}{2,}łem(?![\\p{L}])");

    private final MatchService matching;
    private final SkillDictionary dictionary;
    private final ProfileService profiles;
    private final OfferService offers;

    public CvTailoringService(MatchService matching, SkillDictionary dictionary, ProfileService profiles,
                              OfferService offers) {
        this.matching = matching;
        this.dictionary = dictionary;
        this.profiles = profiles;
        this.offers = offers;
    }

    /**
     * Umiejętność z profilu, której brakuje w CV.
     *
     * @param skill        nazwa kanoniczna
     * @param requirement  wymaganie z oferty, którego dotyczy
     * @param mustHave     czy wymaganie jest obowiązkowe
     * @param profileYears lata z profilu
     * @param cvLine       linia do sekcji „Umiejętności” (np. „Docker (3 lata)”)
     * @param sentence     szkic zdania do opisu doświadczenia — fragmenty w [nawiasach] do uzupełnienia
     * @param scoreGain    o ile punktów wzrośnie wynik po dopisaniu samej tej linii
     */
    public record ProfileAddition(String skill, String requirement, boolean mustHave, Integer profileYears,
                                  String cvLine, String sentence, int scoreGain) {
    }

    /**
     * Brak umiejętności, ale w CV jest pokrewna.
     *
     * @param evidence cytat z CV z pokrewną umiejętnością (dosłowny fragment)
     */
    public record RelatedSkill(String skill, String relatedSkill, String requirement, boolean mustHave,
                               String evidence, String advice, String sentence) {
    }

    /** Akcja dla braku: nauka, pominięcie, opis tylko jeśli prawdziwy. */
    public enum MissingAction {
        LEARN, SKIP, DESCRIBE_IF_TRUE
    }

    public record MissingItem(String skill, String requirement, boolean mustHave, MissingAction action,
                              String advice) {
    }

    /** Wymaganie częściowo spełnione (np. za mało lat) — uwaga z dopasowania. */
    public record RequirementNote(String requirement, boolean mustHave, String note) {
    }

    public record TailoringResult(Long offerId, String offerTitle, Long cvId, String cvName, int currentScore,
                                  int projectedScore, List<ProfileAddition> fromProfile, List<RelatedSkill> related,
                                  List<MissingItem> missing, List<RequirementNote> notes, String cvAddition,
                                  String summary) {
    }

    public TailoringResult tailor(Long offerId, Long cvId) {
        JobOffer offer = offers.get(offerId);
        CvVariant cv = profiles.cvOrActive(cvId).orElse(null);
        return tailor(offer, cv, profiles.profile());
    }

    private record RelatedPair(String skill, String related) {
    }

    TailoringResult tailor(JobOffer offer, CvVariant cv, CandidateProfile profile) {
        if (cv == null) {
            return new TailoringResult(offer.getId(), offer.getTitle(), null, null, 0, 0, List.of(), List.of(),
                    List.of(), List.of(), "", "Dodaj CV w profilu, aby dopasować je do tej oferty.");
        }
        String text = Text.nullToEmpty(cv.getContent());
        List<Requirement> reqs = offer.getRequirements() == null ? List.of() : offer.getRequirements();
        MatchResult current = matching.match(offer, cv, profile);
        Map<String, SkillHit> inCv = skillsInCv(text);
        Map<String, SkillEntry> inProfile = new HashMap<>();
        for (SkillEntry e : profile == null ? List.<SkillEntry>of() : profile.getSkills()) {
            if (e != null && !Text.isBlank(e.name())) {
                inProfile.put(dictionary.canonicalName(e.name()), e);
            }
        }
        String gender = genderOf(text);

        Map<String, ProfileAddition> additions = new LinkedHashMap<>();
        Map<String, RelatedSkill> related = new LinkedHashMap<>();
        Map<String, MissingItem> missing = new LinkedHashMap<>();
        List<RequirementNote> notes = new ArrayList<>();

        for (RequirementMatch rm : current.requirements()) {
            if (rm.level() == MatchLevel.FULL) {
                continue;
            }
            if (rm.skills().isEmpty()) {
                if (rm.level() == MatchLevel.MISSING) {
                    missing.putIfAbsent("req:" + rm.requirement(), new MissingItem(null, rm.requirement(),
                            rm.mustHave(), MissingAction.DESCRIBE_IF_TRUE,
                            "Słownik nie rozpoznał tu konkretnej umiejętności. Jeśli masz takie doświadczenie, opisz je "
                                    + "w CV słowami zbliżonymi do ogłoszenia (ATS porównuje słowa kluczowe). "
                                    + "Jeśli nie masz — pomiń, nie dopisuj."));
                } else if (rm.note() != null) {
                    notes.add(new RequirementNote(rm.requirement(), rm.mustHave(), rm.note()));
                }
                continue;
            }
            List<String> present = new ArrayList<>();
            List<String> fromProfile = new ArrayList<>();
            List<RelatedPair> relatedPairs = new ArrayList<>();
            List<String> absent = new ArrayList<>();
            for (String skill : rm.skills()) {
                if (inCv.containsKey(skill)) {
                    present.add(skill);
                } else if (inProfile.containsKey(skill)) {
                    fromProfile.add(skill);
                } else {
                    String rel = relatedInCv(skill, inCv);
                    if (rel != null) {
                        relatedPairs.add(new RelatedPair(skill, rel));
                    } else {
                        absent.add(skill);
                    }
                }
            }
            boolean anyOf = isAnyOf(rm.requirement());
            if ((anyOf && !present.isEmpty()) || (fromProfile.isEmpty() && relatedPairs.isEmpty() && absent.isEmpty())) {
                // wymaganie pokryte przez CV, ale częściowo (lata, poziom języka) — tylko uwaga
                if (rm.note() != null) {
                    notes.add(new RequirementNote(rm.requirement(), rm.mustHave(), rm.note()));
                }
                continue;
            }
            if (anyOf && !fromProfile.isEmpty()) {
                // „X lub Y” — wystarczy jedna umiejętność z profilu
                fromProfile = List.of(fromProfile.get(0));
                relatedPairs = List.of();
                absent = List.of();
            } else if (anyOf && !relatedPairs.isEmpty()) {
                relatedPairs = List.of(relatedPairs.get(0));
                absent = List.of();
            } else if (anyOf && absent.size() > 1) {
                // „Go lub Rust” — jeden brak z alternatywami
                absent = List.of(String.join(" / ", absent));
            }
            for (String skill : fromProfile) {
                ProfileAddition prev = additions.get(skill);
                if (prev == null || (!prev.mustHave() && rm.mustHave())) {
                    SkillEntry e = inProfile.get(skill);
                    additions.put(skill, new ProfileAddition(skill, rm.requirement(), rm.mustHave(), e.years(),
                            cvLine(skill, e), sentence(skill, e, gender), 0));
                }
            }
            for (RelatedPair pair : relatedPairs) {
                RelatedSkill prev = related.get(pair.skill());
                if (prev == null || (rm.mustHave() && !prev.mustHave())) {
                    SkillHit h = inCv.get(pair.related());
                    String quote = Text.quoteAround(text, h.start(), h.end(), QUOTE_MAX);
                    related.put(pair.skill(), new RelatedSkill(pair.skill(), pair.related(), rm.requirement(),
                            rm.mustHave(), text.contains(quote) ? quote : null,
                            relatedAdvice(pair.skill(), pair.related()),
                            relatedSentence(pair.skill(), pair.related(), inProfile.get(pair.related()))));
                }
            }
            for (String skill : absent) {
                MissingItem prev = missing.get(skill);
                if (prev == null || (rm.mustHave() && !prev.mustHave())) {
                    missing.put(skill, missingItem(skill, rm.requirement(), rm.mustHave()));
                }
            }
        }

        // przewidywany wynik: CV + linie z profilu (każda osobno i wszystkie razem)
        List<ProfileAddition> fromProfile = new ArrayList<>();
        for (ProfileAddition a : additions.values()) {
            int score = matching.matchRequirements(reqs, append(text, List.of(a.cvLine())), profile).score();
            fromProfile.add(new ProfileAddition(a.skill(), a.requirement(), a.mustHave(), a.profileYears(),
                    a.cvLine(), a.sentence(), Math.max(0, score - current.score())));
        }
        fromProfile.sort((a, b) -> Boolean.compare(b.mustHave(), a.mustHave()) != 0
                ? Boolean.compare(b.mustHave(), a.mustHave()) : Integer.compare(b.scoreGain(), a.scoreGain()));
        List<String> lines = fromProfile.stream().map(ProfileAddition::cvLine).toList();
        int projected = lines.isEmpty() ? current.score()
                : Math.max(current.score(), matching.matchRequirements(reqs, append(text, lines), profile).score());

        List<MissingItem> missingList = new ArrayList<>(missing.values());
        missingList.sort((a, b) -> Boolean.compare(b.mustHave(), a.mustHave()));
        List<RelatedSkill> relatedList = new ArrayList<>(related.values());
        relatedList.sort((a, b) -> Boolean.compare(b.mustHave(), a.mustHave()));
        return new TailoringResult(offer.getId(), offer.getTitle(), cv.getId(), cv.getName(), current.score(),
                projected, fromProfile, relatedList, missingList, notes, String.join("\n", lines),
                summary(current.score(), projected, fromProfile.size(), relatedList.size(), missingList, reqs.isEmpty()));
    }

    // ------------------------------------------------------------------ propozycje

    /** Linia do sekcji „Umiejętności” — tylko fakty z profilu (lata), bez dopowiedzeń. */
    String cvLine(String skill, SkillEntry e) {
        Skill def = dictionary.get(skill).orElse(null);
        if (def != null && def.isLanguage()) {
            return skill + " — poziom [wpisz poziom, np. B2]";
        }
        boolean yearsMakeSense = def == null || !CvSkillExtractor.NO_YEARS_CATEGORIES.contains(def.category());
        if (e != null && e.years() != null && e.years() > 0 && yearsMakeSense) {
            return skill + " (" + Plural.years(e.years()) + ")";
        }
        return skill;
    }

    /** Szkic zdania do opisu doświadczenia; [nawiasy] oznaczają miejsca do uzupełnienia prawdziwymi faktami. */
    String sentence(String skill, SkillEntry e, String gender) {
        Skill def = dictionary.get(skill).orElse(null);
        String category = def == null ? "" : def.category();
        return switch (category) {
            case "jezyk-obcy" -> skill + " — poziom [wpisz poziom, np. B2]; [gdzie go używasz, np. codzienna "
                    + "komunikacja z zespołem lub klientem].";
            case "miekkie" -> skill + ": [konkretna sytuacja z pracy, w której ta umiejętność dała efekt — "
                    + "i jaki to był efekt].";
            case "uprawnienia" -> skill + ".";
            case "wyksztalcenie" -> skill + ": [uczelnia, kierunek, rok ukończenia].";
            default -> {
                String years = e != null && e.years() != null && e.years() > 0
                        ? " (" + Plural.years(e.years()) + " doświadczenia)" : "";
                yield skill + years + ": w [nazwa projektu lub firmy] " + verb(gender, "wykorzystał") + " "
                        + noun(category) + " do [cel], co [efekt, najlepiej z liczbą, np. skróciło czas wdrożenia "
                        + "o 30%].";
            }
        };
    }

    /** „tę technologię”, „tę metodykę”, „to narzędzie”, „tę umiejętność” — zależnie od kategorii ze słownika. */
    private static String noun(String category) {
        return switch (category) {
            case "metodyka" -> "tę metodykę";
            case "narzedzie", "biuro" -> "to narzędzie";
            case "jezyk-programowania", "framework", "baza-danych", "devops", "chmura", "testy", "dane", "frontend",
                 "platforma", "architektura", "bezpieczenstwo" -> "tę technologię";
            default -> "tę umiejętność";
        };
    }

    private static String relatedAdvice(String skill, String related) {
        return "Masz w CV " + related + " — technologię z tej samej rodziny co " + skill + ". Opisz konkretnie "
                + "swoje doświadczenie z " + related + " i podkreśl elementy wspólne. Nie wpisuj " + skill
                + " jako umiejętności, jeśli nie masz z nim doświadczenia — w liście motywacyjnym możesz napisać, "
                + "że chcesz go poznać.";
    }

    private static String relatedSentence(String skill, String related, SkillEntry relatedInProfile) {
        String years = relatedInProfile != null && relatedInProfile.years() != null && relatedInProfile.years() > 0
                ? " (" + Plural.years(relatedInProfile.years()) + ")" : "";
        return related + years + ": [projekt i efekt] — technologia z tej samej rodziny co " + skill + ".";
    }

    private static MissingItem missingItem(String skill, String requirement, boolean mustHave) {
        if (mustHave) {
            return new MissingItem(skill, requirement, true, MissingAction.LEARN,
                    "Wymaganie obowiązkowe, którego nie ma ani w CV, ani w profilu. Nie dopisuj go do CV. Jeśli to "
                            + "pojedynczy brak, możesz aplikować mimo to i pokazać gotowość nauki; jeśli " + skill
                            + " powtarza się w ofertach — dodaj go do planu nauki.");
        }
        return new MissingItem(skill, requirement, false, MissingAction.SKIP,
                "Mile widziane — możesz to pominąć. Jeśli " + skill + " pojawia się w wielu ofertach, rozważ naukę.");
    }

    private static String summary(int current, int projected, int additions, int related, List<MissingItem> missing,
                                  boolean noRequirements) {
        if (noRequirements) {
            return "Oferta nie ma wymagań do porównania — dodaj je w edycji oferty.";
        }
        StringBuilder sb = new StringBuilder();
        if (additions > 0) {
            sb.append("Dopisanie ").append(additions).append(" ")
                    .append("umiejętności")
                    .append(" z profilu może podnieść dopasowanie z ").append(current).append("% do ")
                    .append(projected).append("%.");
        } else {
            sb.append("CV zawiera już wszystkie wymagane umiejętności, które masz w profilu (dopasowanie ")
                    .append(current).append("%).");
        }
        if (related > 0) {
            sb.append(" ").append(related).append(" ")
                    .append(Plural.of(related, "wymaganie możesz", "wymagania możesz", "wymagań możesz"))
                    .append(" pokryć opisem pokrewnego doświadczenia.");
        }
        long mustMissing = missing.stream().filter(MissingItem::mustHave).count();
        if (mustMissing > 0) {
            sb.append(" Braki obowiązkowe: ").append(mustMissing).append(" — tych nie dopisuj, rozważ naukę.");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ pomocnicze

    /** Umiejętności w CV z relacją „obejmuje” (jak w MatchService): nazwa → wzmianka będąca dowodem. */
    private Map<String, SkillHit> skillsInCv(String text) {
        Map<String, SkillHit> inCv = new LinkedHashMap<>();
        for (SkillHit h : dictionary.find(text)) {
            inCv.putIfAbsent(h.skill(), h);
        }
        for (SkillHit h : new ArrayList<>(inCv.values())) {
            for (String implied : dictionary.impliedBy(h.skill())) {
                inCv.putIfAbsent(implied, h);
            }
        }
        return inCv;
    }

    private String relatedInCv(String skill, Map<String, SkillHit> inCv) {
        for (String has : inCv.keySet()) {
            if (dictionary.sameFamily(skill, has)) {
                return has;
            }
        }
        return null;
    }

    /** Ta sama heurystyka „którykolwiek z” co w MatchService. */
    static boolean isAnyOf(String requirement) {
        String f = Text.fold(requirement);
        return f.contains(" lub ") || f.contains(" albo ") || f.contains(" or ") || f.contains("jednego z")
                || f.contains("one of") || f.contains("/");
    }

    private static String append(String text, List<String> lines) {
        return text + "\n\n" + String.join("\n", lines);
    }

    /**
     * Forma czasowników w CV: „f” (np. „wdrożyłam”), „m” („wdrożyłem”) albo null, gdy nie da się ustalić.
     * Dzięki temu szkic zdania pasuje do stylu CV.
     */
    static String genderOf(String text) {
        int f = count(FEMININE, text);
        int m = count(MASCULINE, text);
        if (f > m) {
            return "f";
        }
        if (m > f) {
            return "m";
        }
        return null;
    }

    private static int count(Pattern p, String text) {
        Matcher matcher = p.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }

    /** „użył” → „użyłam” / „użyłem” / „użyłam/em”. */
    static String verb(String gender, String stem) {
        if ("f".equals(gender)) {
            return stem + "am";
        }
        if ("m".equals(gender)) {
            return stem + "em";
        }
        return stem + "am/em";
    }
}

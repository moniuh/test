package pl.radarpracy.profile.cv;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Automatyczna ekstrakcja umiejętności z CV (pomysł #2).
 *
 * <p>Słownik ({@link SkillDictionary}) rozpoznaje umiejętności wraz z synonimami. Lata
 * doświadczenia bierzemy z {@link YearsParser#yearsInText}, ale tylko gdy przypisanie jest
 * wiarygodne:</p>
 * <ol>
 *     <li>fragment zdania (między przecinkami/średnikami) zawiera i umiejętność, i liczbę lat —
 *     np. „4 lata z Java i Spring Boot, 2 lata z React” daje Java 4, React 2;</li>
 *     <li>albo cała linia zawiera liczbę lat (lub zakres dat) i wspomina <em>tylko tę jedną</em>
 *     umiejętność — np. „Java Developer — PayFlow (2022 – obecnie)”.</li>
 * </ol>
 * <p>Przy kilku wzmiankach bierzemy największą wartość. Każdy cytat jest dosłownym fragmentem CV.</p>
 */
@Service
public class CvSkillExtractor {

    static final int QUOTE_MAX = 200;
    /** Kategorie, dla których „lata doświadczenia” nie mają sensu. */
    static final Set<String> NO_YEARS_CATEGORIES = Set.of("jezyk-obcy", "wyksztalcenie", "uprawnienia", "miekkie");
    /** „4 latami” — forma narzędnika, której YearsParser nie rozpoznaje; zamieniamy ją na „4 lat”. */
    private static final Pattern INSTRUMENTAL = Pattern.compile("(\\d{1,2})\\s*latami");
    /** „2,5 roku” — YearsParser odczytałby „5 roku”; zaokrąglamy w dół do pełnych lat. */
    private static final Pattern DECIMAL = Pattern.compile("(\\d{1,2})[,.]\\d+(?=\\s*(?:roku|lat|lata|years?))");

    /**
     * Aliasy słownika, które są też zwykłymi polskimi słowami lub skrótami: „jest”, „go”, „r.” (rok), „c”, „sem.”.
     * Uznajemy je tylko, gdy w CV zaczynają się wielką literą („Jest”, „Go”, „R”, „C”, „SEM”).
     */
    static final Set<String> AMBIGUOUS_ALIASES = Set.of("jest", "go", "r", "c", "sem");

    private final SkillDictionary dictionary;
    private final ProfileService profiles;

    public CvSkillExtractor(SkillDictionary dictionary, ProfileService profiles) {
        this.dictionary = dictionary;
        this.profiles = profiles;
    }

    /**
     * Umiejętność rozpoznana w CV.
     *
     * @param name             nazwa kanoniczna ze słownika
     * @param category         kategoria ze słownika (np. „framework”)
     * @param years            lata doświadczenia wynikające z CV albo null, gdy nie da się ich wiarygodnie ustalić
     * @param evidence         cytat z CV z pierwszą wzmianką (dosłowny fragment)
     * @param yearsEvidence    cytat, z którego wzięto lata (dosłowny fragment) albo null
     * @param mentions         liczba wzmianek w CV
     * @param alreadyInProfile czy umiejętność jest już w profilu
     * @param profileYears     lata zapisane w profilu (jeśli jest w profilu)
     */
    public record SkillSuggestion(String name, String category, Integer years, String evidence, String yearsEvidence,
                                  int mentions, boolean alreadyInProfile, Integer profileYears) {
    }

    public record CvSkillsResult(Long cvId, String cvName, List<SkillSuggestion> suggestions, int newCount) {
    }

    public record ApplyRequest(List<String> names) {
    }

    /**
     * Wynik dopisania umiejętności do profilu.
     *
     * @param added   dodane nazwy kanoniczne
     * @param skipped pominięte (z powodem)
     * @param profile profil po zapisie
     */
    public record ApplyResult(List<String> added, List<String> skipped, CandidateProfile profile) {
    }

    public CvSkillsResult suggestions(Long cvId) {
        CvVariant cv = profiles.cv(cvId);
        List<SkillSuggestion> list = suggest(cv.getContent(), profiles.profile());
        int fresh = (int) list.stream().filter(s -> !s.alreadyInProfile()).count();
        return new CvSkillsResult(cv.getId(), cv.getName(), list, fresh);
    }

    /** Dopisuje wybrane umiejętności z CV do profilu (istniejące wpisy zostają bez zmian). */
    @Transactional
    public ApplyResult apply(Long cvId, List<String> names) {
        if (names == null || names.stream().allMatch(Text::isBlank)) {
            throw new BadRequestException("Wybierz co najmniej jedną umiejętność do dodania.");
        }
        CvVariant cv = profiles.cv(cvId);
        CandidateProfile profile = profiles.profile();
        Map<String, SkillSuggestion> found = new HashMap<>();
        for (SkillSuggestion s : suggest(cv.getContent(), profile)) {
            found.put(key(s.name()), s);
        }
        Set<String> existing = new HashSet<>();
        for (SkillEntry e : profile.getSkills()) {
            existing.add(key(dictionary.canonicalName(e.name())));
        }
        List<SkillEntry> skills = new ArrayList<>(profile.getSkills());
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String raw : new LinkedHashSet<>(names)) {
            if (Text.isBlank(raw)) {
                continue;
            }
            String canonical = dictionary.canonicalName(raw);
            SkillSuggestion s = found.get(key(canonical));
            if (s == null) {
                skipped.add(raw.strip() + " — nie występuje w tej wersji CV");
            } else if (existing.contains(key(s.name()))) {
                skipped.add(s.name() + " — już jest w profilu");
            } else {
                skills.add(new SkillEntry(s.name(), null, s.years()));
                existing.add(key(s.name()));
                added.add(s.name());
            }
        }
        if (!added.isEmpty()) {
            profile.setSkills(skills);
            profile = profiles.update(profile);
        }
        return new ApplyResult(added, skipped, profile);
    }

    /**
     * Wzmianki umiejętności bez fałszywych trafień niejednoznacznych aliasów (np. „jest” jako czasownik,
     * „2020 r.” jako język R) — zob. {@link #AMBIGUOUS_ALIASES}.
     */
    static List<SkillHit> reliableHits(SkillDictionary dictionary, String text) {
        List<SkillHit> out = new ArrayList<>();
        for (SkillHit h : dictionary.find(text)) {
            if (AMBIGUOUS_ALIASES.contains(h.alias()) && !Character.isUpperCase(text.charAt(h.start()))) {
                continue;
            }
            out.add(h);
        }
        return out;
    }

    /** Umiejętności rozpoznane w tekście CV w porównaniu z profilem (logika bez dostępu do bazy). */
    List<SkillSuggestion> suggest(String cvText, CandidateProfile profile) {
        String text = Text.nullToEmpty(cvText);
        List<SkillHit> hits = reliableHits(dictionary, text);
        Map<String, List<SkillHit>> bySkill = new LinkedHashMap<>();
        for (SkillHit h : hits) {
            bySkill.computeIfAbsent(h.skill(), k -> new ArrayList<>()).add(h);
        }
        Map<String, SkillEntry> inProfile = new HashMap<>();
        if (profile != null) {
            for (SkillEntry e : profile.getSkills()) {
                if (e != null && !Text.isBlank(e.name())) {
                    inProfile.put(key(dictionary.canonicalName(e.name())), e);
                }
            }
        }
        List<SkillSuggestion> out = new ArrayList<>();
        for (var entry : bySkill.entrySet()) {
            String name = entry.getKey();
            List<SkillHit> own = entry.getValue();
            String category = dictionary.get(name).map(Skill::category).orElse(null);
            SkillHit first = own.get(0);
            String evidence = verified(text, lineQuote(text, first.start(), first.end(), QUOTE_MAX));
            YearsFound years = NO_YEARS_CATEGORIES.contains(category) ? null : years(text, own, hits);
            SkillEntry declared = inProfile.get(key(name));
            out.add(new SkillSuggestion(name, category, years == null ? null : years.years(), evidence,
                    years == null ? null : years.quote(), own.size(), declared != null,
                    declared == null ? null : declared.years()));
        }
        // najpierw nowe (których nie ma w profilu), potem w kolejności występowania w CV
        out.sort(Comparator.comparing(SkillSuggestion::alreadyInProfile));
        return out;
    }

    record YearsFound(int years, String quote) {
    }

    /** Lata doświadczenia dla jednej umiejętności — według reguł opisanych w Javadoc klasy. */
    YearsFound years(String text, List<SkillHit> ownHits, List<SkillHit> allHits) {
        YearsFound best = null;
        for (SkillHit h : ownHits) {
            int lineFrom = text.lastIndexOf('\n', h.start() - 1) + 1;
            int lineTo = text.indexOf('\n', h.end());
            lineTo = lineTo < 0 ? text.length() : lineTo;
            int clauseFrom = lineFrom;
            for (int i = h.start() - 1; i >= lineFrom; i--) {
                if (isClauseSeparator(text, i)) {
                    clauseFrom = i + 1;
                    break;
                }
            }
            int clauseTo = lineTo;
            for (int i = h.end(); i < lineTo; i++) {
                if (isClauseSeparator(text, i)) {
                    clauseTo = i;
                    break;
                }
            }
            String clause = text.substring(clauseFrom, clauseTo);
            Integer y = yearsIn(clause);
            String quote = clause.strip();
            if (y == null) {
                String line = text.substring(lineFrom, lineTo);
                Integer lineYears = yearsIn(line);
                if (lineYears != null && distinctSkillsBetween(allHits, lineFrom, lineTo) == 1) {
                    y = lineYears;
                    quote = line.strip();
                }
            }
            if (y != null && (best == null || y > best.years())) {
                best = new YearsFound(y, verified(text, quote));
            }
        }
        return best;
    }

    /**
     * Granica fragmentu zdania: „,” (ale nie przecinek dziesiętny „2,5”), „;”, „|”, „•” oraz koniec zdania
     * („. ”, „! ”, „? ” przed wielką literą — „sp. z o.o.” nie dzieli zdania).
     */
    static boolean isClauseSeparator(String text, int i) {
        char c = text.charAt(i);
        if (c == ',') {
            boolean decimal = i > 0 && i + 1 < text.length() && Character.isDigit(text.charAt(i - 1))
                    && Character.isDigit(text.charAt(i + 1));
            return !decimal;
        }
        if (c == ';' || c == '|' || c == '•') {
            return true;
        }
        if (c == '.' || c == '!' || c == '?') {
            return i + 2 < text.length() && text.charAt(i + 1) == ' ' && Character.isUpperCase(text.charAt(i + 2));
        }
        return false;
    }

    private static Integer yearsIn(String fragment) {
        String f = INSTRUMENTAL.matcher(Text.fold(fragment)).replaceAll("$1 lat");
        f = DECIMAL.matcher(f).replaceAll("$1");
        return YearsParser.yearsInText(f);
    }

    private static int distinctSkillsBetween(List<SkillHit> hits, int from, int to) {
        Set<String> names = new HashSet<>();
        for (SkillHit h : hits) {
            if (h.start() >= from && h.end() <= to) {
                names.add(h.skill());
            }
        }
        return names.size();
    }

    /**
     * Cytat: linia CV zawierająca wzmiankę (bez wiodącego punktora); dłuższa niż {@code maxLen} jest przycinana
     * do okna wokół wzmianki na granicach słów. Zawsze dosłowny podciąg tekstu.
     */
    static String lineQuote(String text, int start, int end, int maxLen) {
        int from = text.lastIndexOf('\n', start - 1) + 1;
        int to = text.indexOf('\n', end);
        to = to < 0 ? text.length() : to;
        if (to - from > maxLen) {
            int half = Math.max(0, (maxLen - (end - start)) / 2);
            int wFrom = Math.max(from, start - half);
            int wTo = Math.min(to, end + half);
            while (wFrom > from && wFrom < start && !Character.isWhitespace(text.charAt(wFrom - 1))) {
                wFrom++;
            }
            while (wTo < to && wTo > end && !Character.isWhitespace(text.charAt(wTo))) {
                wTo--;
            }
            from = wFrom;
            to = wTo;
        }
        String q = text.substring(from, to).strip();
        int lead = 0;
        while (lead < q.length() && "-•*·▪–—>".indexOf(q.charAt(lead)) >= 0) {
            lead++;
        }
        return q.substring(lead).strip();
    }

    /** Zasada „cytat = dosłowny fragment źródła”: zwraca cytat tylko, gdy występuje w tekście. */
    private static String verified(String text, String quote) {
        return quote != null && !quote.isEmpty() && text.contains(quote) ? quote : null;
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}

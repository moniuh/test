package pl.radarpracy.tailoring;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.tailoring.GuardReport.Kind;
import pl.radarpracy.tailoring.GuardReport.Violation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strażnik prawdomówności tailoringu (pomysł #68).
 *
 * <p>Zasady sprawdzane w kodzie (bez AI):</p>
 * <ul>
 *   <li>każda umiejętność ze słownika w wersji dopasowanej jest w CV bazowym, w profilu albo wynika
 *       z nich przez relację „obejmuje” (Spring Boot ⇒ Java);</li>
 *   <li>każda liczba, procent, poziom języka i data występuje w CV bazowym (liczby z profilu —
 *       nagłówek i lata umiejętności — też są dozwolone, bo to dane kandydata);</li>
 *   <li>linie stanowisk (rola, pracodawca, daty) są dosłownie takie jak w CV bazowym i żadne stanowisko
 *       nie znika;</li>
 *   <li>punkty doświadczenia i linie pozostałych sekcji są dosłownie z CV bazowego albo — po
 *       przeredagowaniu — zbudowane z tych samych słów; pozycje list umiejętności pochodzą z CV bazowego;
 *       dane w nagłówku CV się nie zmieniają.</li>
 * </ul>
 * <p>Podsumowanie i sekcja kluczowych kompetencji mogą być nowym tekstem, ale przechodzą kontrolę
 * umiejętności i liczb.</p>
 */
@Component
public class TruthGuard {

    /** Minimalny udział słów przeredagowanej linii, które muszą pochodzić z jednej linii CV bazowego. */
    static final double LINE_OVERLAP = 0.6;
    /** … albo z całego CV bazowego. */
    static final double DOC_OVERLAP = 0.8;

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,/]\\d+)*(?:\\s?%)?");
    private static final Pattern LANG_LEVEL = Pattern.compile("(?<![\\p{L}\\d])[ABC][12](?![\\p{L}\\d])");
    private static final Pattern DATE_LIKE = Pattern.compile("^(?:(?:19|20)\\d{2}|\\d{1,2}[./](?:19|20)\\d{2})$");
    private static final Pattern ITEM_SPLIT = Pattern.compile("\\s*(?:[,;|•]|\\s·\\s)\\s*");

    private final SkillDictionary dictionary;

    public TruthGuard(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /** Fakty z CV bazowego i profilu, względem których sprawdzamy wersję dopasowaną. */
    public record Facts(String baseText, String baseFolded, Set<String> cvSkills, Set<String> allowedSkills,
                        Set<String> numbers, Set<String> levels, Set<String> baseLines, List<Set<String>> lineStems,
                        Set<String> allStems, ParsedCv parsed) {
    }

    public Facts facts(String baseText, CandidateProfile profile) {
        String base = Text.nullToEmpty(baseText);
        Set<String> cvSkills = withImplied(dictionary.skillsIn(base));
        Set<String> allowed = new LinkedHashSet<>(cvSkills);
        Set<String> numbers = new HashSet<>(numberFacts(base));
        Set<String> levels = new HashSet<>(levelsIn(base));
        if (profile != null) {
            Set<String> fromProfile = new LinkedHashSet<>();
            for (SkillEntry s : profile.getSkills()) {
                if (s != null && s.name() != null) {
                    fromProfile.add(dictionary.canonicalName(s.name()));
                    if (s.years() != null) {
                        numbers.add(String.valueOf(s.years()));
                    }
                }
            }
            allowed.addAll(withImplied(fromProfile));
            numbers.addAll(numberFacts(profile.getHeadline()));
            levels.addAll(levelsIn(profile.getHeadline()));
        }
        Set<String> baseLines = new HashSet<>();
        List<Set<String>> lineStems = new ArrayList<>();
        Set<String> allStems = new HashSet<>();
        for (String line : Text.lines(base)) {
            baseLines.add(norm(line));
            Set<String> stems = new HashSet<>(RelevanceScorer.stems(line));
            lineStems.add(stems);
            allStems.addAll(stems);
        }
        ParsedCv parsed = CvStructureParser.parse(base);
        for (ParsedCv.Role role : parsed.roles()) {
            for (ParsedCv.Bullet b : role.bullets()) {
                baseLines.add(norm(b.text()));
            }
        }
        return new Facts(base, Text.fold(base), cvSkills, allowed, numbers, levels, baseLines, lineStems, allStems,
                parsed);
    }

    /** Pełna kontrola wersji dopasowanej. */
    public GuardReport check(String baseText, CandidateProfile profile, String tailoredText) {
        return check(facts(baseText, profile), tailoredText);
    }

    public GuardReport check(Facts facts, String tailoredText) {
        List<Violation> violations = new ArrayList<>();
        String[] lines = Text.nullToEmpty(tailoredText).split("\\R", -1);
        ParsedCv tailored = CvStructureParser.parse(tailoredText);
        Set<String> identity = new HashSet<>();
        for (ParsedCv.Role r : tailored.roles()) {
            r.identityLines().forEach(l -> identity.add(norm(l)));
        }
        Set<String> usedSkills = new LinkedHashSet<>();
        int checked = 0;
        int verbatim = 0;
        int rewritten = 0;
        int generated = 0;
        CvSectionKind section = CvSectionKind.HEADER;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int no = i + 1;
            checked++;
            CvSectionKind heading = CvStructureParser.headingKind(line);
            if (heading != null) {
                section = heading;
                continue;
            }
            violations.addAll(factViolations(no, line, facts, usedSkills));
            String n = norm(line);
            if (facts.baseLines().contains(n)) {
                verbatim++;
                continue;
            }
            switch (section) {
                case HEADER -> violations.add(new Violation(no, Kind.UNVERIFIED_LINE,
                        "Dane w nagłówku CV różnią się od CV bazowego — tailoring nie zmienia danych osobowych.", line));
                case SUMMARY, KEY_COMPETENCES -> generated++;
                case SKILLS, LANGUAGES -> {
                    List<String> unknown = unknownItems(line, facts, dictionary);
                    if (unknown.isEmpty()) {
                        rewritten++;
                    } else {
                        violations.add(new Violation(no, Kind.UNVERIFIED_LINE, "Pozycje spoza CV bazowego: "
                                + String.join(", ", unknown) + ".", line));
                    }
                }
                default -> {
                    if (section == CvSectionKind.EXPERIENCE && identity.contains(n)) {
                        // linie stanowisk sprawdza kontrola stanowisk poniżej
                        continue;
                    }
                    if (supported(line, facts)) {
                        rewritten++;
                    } else {
                        violations.add(new Violation(no, Kind.UNVERIFIED_LINE, section == CvSectionKind.EXPERIENCE
                                ? "Punktu nie da się potwierdzić w CV bazowym — opisuj tylko to, co już jest w CV."
                                : "Linii nie da się potwierdzić w CV bazowym.", line));
                    }
                }
            }
        }
        violations.addAll(roleViolations(facts, tailored, lines));
        violations.sort((a, b) -> Integer.compare(a.line() == 0 ? Integer.MAX_VALUE : a.line(),
                b.line() == 0 ? Integer.MAX_VALUE : b.line()));
        List<String> profileOnly = usedSkills.stream()
                .filter(s -> facts.allowedSkills().contains(s) && !facts.cvSkills().contains(s)).toList();
        return new GuardReport(violations.isEmpty(), List.copyOf(violations), checked, verbatim, rewritten, generated,
                profileOnly);
    }

    /**
     * Kontrola pojedynczego zdania wygenerowanego przez AI w miejsce {@code original}: fakty jak w pełnej
     * kontroli oraz słowa pochodzące z oryginału lub CV bazowego (co najmniej {@code minFromOriginal}
     * słów z samego oryginału). Pusta lista = zdanie przyjęte.
     */
    public List<Violation> checkRewrite(Facts facts, String original, String proposed, double minFromOriginal) {
        List<Violation> out = new ArrayList<>(factViolations(0, proposed, facts, new LinkedHashSet<>()));
        if (!facts.baseLines().contains(norm(proposed))) {
            List<String> stems = RelevanceScorer.stems(proposed);
            Set<String> origStems = new HashSet<>(RelevanceScorer.stems(original == null ? "" : original));
            if (!stems.isEmpty()) {
                long fromOriginal = stems.stream().filter(origStems::contains).count();
                long fromBase = stems.stream().filter(s -> origStems.contains(s) || facts.allStems().contains(s))
                        .count();
                if ((double) fromOriginal / stems.size() < minFromOriginal
                        || (double) fromBase / stems.size() < DOC_OVERLAP) {
                    out.add(new Violation(0, Kind.UNVERIFIED_LINE,
                            "Zdanie odbiega od oryginału — zawiera treści, których nie ma w CV bazowym.", proposed));
                }
            }
        }
        return out;
    }

    /** Same fakty (umiejętności, liczby, daty, poziomy języka) w jednej linii. Pusta lista = linia w porządku. */
    public List<Violation> factCheck(Facts facts, String line) {
        return factViolations(0, line, facts, new LinkedHashSet<>());
    }

    // ------------------------------------------------------------------

    private List<Violation> factViolations(int no, String line, Facts facts, Set<String> usedSkills) {
        List<Violation> out = new ArrayList<>();
        for (String skill : dictionary.skillsIn(line)) {
            usedSkills.add(skill);
            if (!facts.allowedSkills().contains(skill)) {
                out.add(new Violation(no, Kind.NEW_SKILL, "Umiejętność „" + skill
                        + "” nie występuje w CV bazowym ani w profilu — tailoring nie może jej dopisać.", line));
            }
        }
        for (String num : numbersIn(line)) {
            if (allowedNumber(num, facts)) {
                continue;
            }
            boolean date = DATE_LIKE.matcher(num).matches();
            out.add(new Violation(no, date ? Kind.NEW_DATE : Kind.NEW_NUMBER, date
                    ? "Data „" + num + "” nie występuje w CV bazowym."
                    : "Liczba „" + num + "” nie występuje w CV bazowym ani w profilu.", line));
        }
        for (String level : levelsIn(line)) {
            if (!facts.levels().contains(level)) {
                out.add(new Violation(no, Kind.NEW_NUMBER, "Poziom „" + level + "” nie występuje w CV bazowym.", line));
            }
        }
        return out;
    }

    /** Liczba bez % jest dozwolona, gdy w CV występuje w dowolnej postaci; procent — tylko jako procent. */
    private static boolean allowedNumber(String num, Facts facts) {
        return facts.numbers().contains(num);
    }

    private List<Violation> roleViolations(Facts facts, ParsedCv tailored, String[] lines) {
        List<Violation> out = new ArrayList<>();
        List<String> baseIdentity = new ArrayList<>();
        for (ParsedCv.Role r : facts.parsed().roles()) {
            baseIdentity.addAll(r.identityLines());
        }
        Set<String> baseNorm = new HashSet<>();
        baseIdentity.forEach(l -> baseNorm.add(norm(l)));
        Set<String> tailoredAll = new HashSet<>();
        for (String l : lines) {
            if (!l.isBlank()) {
                tailoredAll.add(norm(l));
            }
        }
        Set<String> explained = new HashSet<>();
        for (ParsedCv.Role r : tailored.roles()) {
            for (String l : r.identityLines()) {
                if (baseNorm.contains(norm(l))) {
                    continue;
                }
                int no = lineNumber(lines, l);
                String undated = undated(l);
                String sameButDates = baseIdentity.stream().filter(b -> !tailoredAll.contains(norm(b)))
                        .filter(b -> undated(b).equals(undated)).findFirst().orElse(null);
                if (sameButDates != null) {
                    explained.add(norm(sameButDates));
                    out.add(new Violation(no, Kind.NEW_DATE, "Zmieniono daty stanowiska (w CV bazowym: „"
                            + sameButDates + "”).", l));
                    continue;
                }
                String similar = baseIdentity.stream().filter(b -> !tailoredAll.contains(norm(b)))
                        .filter(b -> overlap(l, b) >= 0.5).findFirst().orElse(null);
                if (similar != null) {
                    explained.add(norm(similar));
                }
                out.add(new Violation(no, Kind.NEW_EMPLOYER, similar != null
                        ? "Zmieniono nazwę stanowiska lub pracodawcy (w CV bazowym: „" + similar + "”)."
                        : "Stanowisko lub pracodawca nie występuje w CV bazowym.", l));
            }
        }
        for (String b : baseIdentity) {
            String n = norm(b);
            if (!tailoredAll.contains(n) && !explained.contains(n)) {
                out.add(new Violation(0, Kind.MISSING_ROLE,
                        "Brak stanowiska lub dat z CV bazowego — tailoring nie usuwa stanowisk ani dat.", b));
            }
        }
        return out;
    }

    private static int lineNumber(String[] lines, String text) {
        String n = norm(text);
        for (int i = 0; i < lines.length; i++) {
            if (norm(lines[i]).equals(n)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Czy przeredagowana linia składa się ze słów CV bazowego. */
    private static boolean supported(String line, Facts facts) {
        List<String> stems = RelevanceScorer.stems(line);
        if (stems.isEmpty()) {
            // linia bez słów treściowych (np. sam punktor lub separator) — nie niesie faktów
            return true;
        }
        long inDoc = stems.stream().filter(facts.allStems()::contains).count();
        if ((double) inDoc / stems.size() >= DOC_OVERLAP) {
            return true;
        }
        for (Set<String> base : facts.lineStems()) {
            long common = stems.stream().filter(base::contains).count();
            if ((double) common / stems.size() >= LINE_OVERLAP) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pozycje listy umiejętności, których nie ma w CV bazowym. Pozycja będąca samą nazwą umiejętności
     * dozwolonej (np. z profilu) jest w porządku — jej prawdziwość sprawdza kontrola umiejętności.
     */
    private static List<String> unknownItems(String line, Facts facts, SkillDictionary dictionary) {
        String content = CvStructureParser.stripBullet(line).strip();
        int colon = content.indexOf(':');
        if (colon > 0 && colon < 40) {
            String label = Text.fold(content.substring(0, colon)).strip();
            if (!label.isEmpty() && !facts.baseFolded().contains(label)) {
                return List.of(content.substring(0, colon).strip());
            }
            content = content.substring(colon + 1);
        }
        List<String> unknown = new ArrayList<>();
        for (String item : ITEM_SPLIT.split(content)) {
            String t = item.strip().replaceAll("[.]+$", "").strip();
            if (t.isEmpty() || facts.baseFolded().contains(Text.fold(t))) {
                continue;
            }
            boolean allowedSkill = dictionary.resolve(t).map(sk -> facts.allowedSkills().contains(sk.name()))
                    .orElse(false);
            if (!allowedSkill) {
                unknown.add(t);
            }
        }
        return unknown;
    }

    private Set<String> withImplied(Set<String> skills) {
        Set<String> out = new LinkedHashSet<>(skills);
        for (String s : skills) {
            out.addAll(dictionary.impliedBy(s));
        }
        return out;
    }

    static List<String> numbersIn(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            // poziomy języka (B2, C1) sprawdzamy osobno
            if (m.start() > 0 && LANG_LEVEL.matcher(text.substring(m.start() - 1, m.end())).matches()) {
                continue;
            }
            out.add(m.group().replace(" ", "").replace(',', '.'));
        }
        return out;
    }

    /** Liczby jako fakty: procent dodatkowo w postaci bez znaku %. */
    static List<String> numberFacts(String text) {
        List<String> out = new ArrayList<>();
        for (String n : numbersIn(text)) {
            out.add(n);
            if (n.endsWith("%")) {
                out.add(n.substring(0, n.length() - 1));
            }
        }
        return out;
    }

    static List<String> levelsIn(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = LANG_LEVEL.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Postać porównawcza linii: bez punktora, z pojedynczymi spacjami. */
    static String norm(String line) {
        return CvStructureParser.stripBullet(Text.nullToEmpty(line)).strip().replaceAll("\\s+", " ");
    }

    private static String undated(String line) {
        return norm(line).replaceAll("\\d", "").replaceAll(
                "(?i)\\b(obecnie|nadal|teraz|present|current|now)\\b", "").replaceAll("\\s+", " ").strip();
    }

    private static double overlap(String a, String b) {
        List<String> sa = RelevanceScorer.stems(a);
        Set<String> sb = new HashSet<>(RelevanceScorer.stems(b));
        if (sa.isEmpty()) {
            return 0;
        }
        return (double) sa.stream().filter(sb::contains).count() / sa.size();
    }
}

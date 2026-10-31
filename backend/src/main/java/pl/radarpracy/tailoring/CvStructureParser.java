package pl.radarpracy.tailoring;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser struktury CV z czystego tekstu (pomysł #67, krok 1).
 *
 * <p>Rozpoznaje nagłówki sekcji po polsku i angielsku (podsumowanie/profil/summary,
 * doświadczenie/experience, umiejętności/skills, języki, wykształcenie/education, projekty,
 * certyfikaty…), stanowiska z datami oraz punkty zaczynające się od „-”, „•”, „*”. Tekst przed
 * pierwszym nagłówkiem to blok danych osobowych. Parser niczego nie zmienia w liniach — tylko je grupuje.</p>
 */
public final class CvStructureParser {

    /** Słowa nagłówków (postać złożona: małe litery, bez polskich znaków). */
    private static final Map<String, CvSectionKind> HEADINGS = new LinkedHashMap<>();

    static {
        put(CvSectionKind.SUMMARY, "podsumowanie", "podsumowanie zawodowe", "profil", "profil zawodowy", "o mnie",
                "summary", "professional summary", "profile", "professional profile", "about me", "objective",
                "cel zawodowy");
        put(CvSectionKind.KEY_COMPETENCES, "kluczowe kompetencje", "kluczowe kompetencje pod to stanowisko",
                "kompetencje kluczowe", "key skills", "core competencies", "key competencies");
        put(CvSectionKind.EXPERIENCE, "doswiadczenie", "doswiadczenie zawodowe", "historia zatrudnienia",
                "przebieg pracy zawodowej", "przebieg kariery", "experience", "work experience",
                "professional experience", "employment history", "career history");
        put(CvSectionKind.SKILLS, "umiejetnosci", "umiejetnosci techniczne", "umiejetnosci twarde",
                "umiejetnosci miekkie", "kompetencje", "technologie", "stack technologiczny", "tech stack",
                "narzedzia", "jezyki programowania", "skills", "technical skills", "hard skills", "soft skills",
                "tools", "technologies");
        put(CvSectionKind.LANGUAGES, "jezyki", "jezyki obce", "znajomosc jezykow", "znajomosc jezykow obcych",
                "languages", "language skills");
        put(CvSectionKind.EDUCATION, "wyksztalcenie", "edukacja", "education", "academic background");
        put(CvSectionKind.PROJECTS, "projekty", "wybrane projekty", "projekty wlasne", "projects",
                "selected projects", "side projects", "portfolio");
        put(CvSectionKind.CERTIFICATES, "certyfikaty", "certyfikaty i szkolenia", "szkolenia",
                "szkolenia i certyfikaty", "kursy", "kursy i szkolenia", "certyfikacje", "certifications",
                "certificates", "courses", "licenses and certifications");
        put(CvSectionKind.OTHER, "zainteresowania", "hobby", "pasje", "osiagniecia", "nagrody", "publikacje",
                "wolontariat", "dodatkowe informacje", "inne", "interests", "achievements", "awards",
                "publications", "volunteering", "additional information");
    }

    private static final List<String> KEYWORDS_LONGEST_FIRST = HEADINGS.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-•*–·▪●◦‣►>]|\\d{1,2}[.)])\\s+");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
    private static final Pattern PRESENT = Pattern.compile(
            "\\b(?:obecnie|nadal|teraz|present|current|now)\\b");

    private CvStructureParser() {
    }

    private static void put(CvSectionKind kind, String... words) {
        for (String w : words) {
            HEADINGS.put(w, kind);
        }
    }

    public static ParsedCv parse(String text) {
        List<String> raw = List.of(Text.nullToEmpty(text).split("\\R", -1));
        List<ParsedCv.Section> sections = new ArrayList<>();
        CvSectionKind kind = CvSectionKind.HEADER;
        String heading = null;
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            CvSectionKind h = headingKind(line);
            if (h != null) {
                sections.add(section(kind, heading, lines));
                kind = h;
                heading = line.strip();
                lines = new ArrayList<>();
            } else {
                lines.add(stripTrailing(line));
            }
        }
        sections.add(section(kind, heading, lines));
        // pusty blok nagłówka (CV zaczyna się od nagłówka sekcji) zostawiamy — upraszcza składanie
        return new ParsedCv(List.copyOf(sections));
    }

    private static ParsedCv.Section section(CvSectionKind kind, String heading, List<String> lines) {
        List<String> trimmed = trimBlank(lines);
        List<ParsedCv.Role> roles = kind == CvSectionKind.EXPERIENCE ? roles(trimmed) : List.of();
        return new ParsedCv.Section(kind, heading, List.copyOf(trimmed), roles);
    }

    /** Rodzaj sekcji, jeśli linia jest nagłówkiem; w przeciwnym razie null. */
    static CvSectionKind headingKind(String line) {
        if (line == null) {
            return null;
        }
        String t = line.strip();
        if (t.isEmpty() || t.length() > 60 || isBullet(t) || t.chars().anyMatch(Character::isDigit)) {
            return null;
        }
        boolean markdown = t.startsWith("#");
        t = t.replaceAll("^#+\\s*", "").replaceAll("^[=*_]+|[=*_]+$", "").strip();
        boolean colon = t.endsWith(":");
        if (colon) {
            t = t.substring(0, t.length() - 1).strip();
        }
        if (t.isEmpty() || t.contains(":") || t.split("\\s+").length > 6) {
            return null;
        }
        boolean upper = t.equals(t.toUpperCase()) && t.chars().anyMatch(Character::isLetter);
        String folded = Text.fold(t).replaceAll("[^a-z/& ]", " ").replaceAll("\\s+", " ").strip();
        CvSectionKind exact = HEADINGS.get(folded);
        if (exact != null) {
            return exact;
        }
        if (!(upper || colon || markdown)) {
            return null;
        }
        for (String k : KEYWORDS_LONGEST_FIRST) {
            if (folded.startsWith(k + " ") || folded.startsWith(k + "/") || folded.startsWith(k + " &")) {
                return HEADINGS.get(k);
            }
        }
        return null;
    }

    /**
     * Stanowiska: linia niebędąca punktem zaczyna nowe stanowisko, gdy poprzednie ma już punkty,
     * gdy nie ma jeszcze żadnego stanowiska albo gdy po pustej linii pojawia się linia z datami.
     * Pozostałe linie bez punktora to szczegóły stanowiska (np. osobna linia z firmą i datami).
     */
    static List<ParsedCv.Role> roles(List<String> lines) {
        List<ParsedCv.Role> out = new ArrayList<>();
        String header = null;
        List<String> details = new ArrayList<>();
        List<List<String>> bullets = new ArrayList<>();
        boolean started = false;
        boolean blankBefore = false;
        boolean lastWasBullet = false;
        for (String line : lines) {
            if (line.isBlank()) {
                blankBefore = true;
                lastWasBullet = false;
                continue;
            }
            if (isBullet(line)) {
                started = true;
                List<String> b = new ArrayList<>();
                b.add(line);
                bullets.add(b);
                lastWasBullet = true;
            } else if (lastWasBullet && !blankBefore && isContinuation(line)) {
                bullets.get(bullets.size() - 1).add(line);
            } else {
                boolean newRole = !started || !bullets.isEmpty() || (blankBefore && hasDate(line));
                if (newRole) {
                    if (started) {
                        out.add(role(header, details, bullets));
                    }
                    header = line;
                    details = new ArrayList<>();
                    bullets = new ArrayList<>();
                    started = true;
                } else {
                    details.add(line);
                }
                lastWasBullet = false;
            }
            blankBefore = false;
        }
        if (started) {
            out.add(role(header, details, bullets));
        }
        return List.copyOf(out);
    }

    private static ParsedCv.Role role(String header, List<String> details, List<List<String>> bullets) {
        return new ParsedCv.Role(header, List.copyOf(details),
                bullets.stream().map(b -> new ParsedCv.Bullet(List.copyOf(b))).toList());
    }

    public static boolean isBullet(String line) {
        return line != null && BULLET.matcher(line).find();
    }

    /** Linia bez punktora (wiodące wcięcie i punktor usunięte). */
    public static String stripBullet(String line) {
        return BULLET.matcher(line).replaceFirst("");
    }

    /** Kontynuacja zawiniętego punktu: wcięta linia zaczynająca się małą literą lub wcięciem ≥ 2. */
    private static boolean isContinuation(String line) {
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        return indent >= 2;
    }

    /** Czy linia zawiera datę (rok 19xx/20xx albo „obecnie”/„present”). */
    public static boolean hasDate(String line) {
        if (line == null) {
            return false;
        }
        return YEAR.matcher(line).find() || PRESENT.matcher(Text.fold(line)).find();
    }

    private static List<String> trimBlank(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).isBlank()) {
            from++;
        }
        while (to > from && lines.get(to - 1).isBlank()) {
            to--;
        }
        return new ArrayList<>(lines.subList(from, to));
    }

    private static String stripTrailing(String s) {
        return s.stripTrailing();
    }
}

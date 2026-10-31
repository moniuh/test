package pl.radarpracy.profile.cv;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prosta struktura CV: linie z pozycją w tekście, nagłówki sekcji i punkty (wypunktowania).
 *
 * <p>Nagłówek sekcji to krótka linia (do {@value #HEADING_MAX_CHARS} znaków), która zaczyna się od
 * słowa kluczowego sekcji, albo linia „Słowo kluczowe: treść”. Wszystkie pozycje odnoszą się do
 * oryginalnego tekstu, więc cytaty pozostają jego dosłownymi fragmentami.</p>
 */
final class CvStructure {

    static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[-'’][\\p{L}\\p{N}]+)*");
    static final int HEADING_MAX_CHARS = 45;
    private static final String BULLET_CHARS = "-•*·▪–—►▸✓✔◦○●■□➢➤>";
    private static final Pattern NUMBERED = Pattern.compile("^\\d{1,2}[.)]\\s+\\S.*");
    /** Słowa, które z dwukropkiem zwykle nie są nagłówkiem („Profil LinkedIn: …”, „Portfolio: …”). */
    private static final Set<String> NO_LABEL = Set.of("profil", "profile", "portfolio");

    /** Rodzaj sekcji CV. */
    enum SectionType {
        SUMMARY, EXPERIENCE, SKILLS, EDUCATION, LANGUAGES, PROJECTS, CERTIFICATES, INTERESTS, OTHER
    }

    /** Słowa kluczowe nagłówków (w postaci złożonej: małe litery, bez polskich znaków). */
    private static final Map<SectionType, List<String>> HEADINGS = new EnumMap<>(Map.of(
            SectionType.SUMMARY, List.of("podsumowanie", "o mnie", "profil zawodowy", "profil", "summary",
                    "profile", "about me", "cel zawodowy", "objective"),
            SectionType.EXPERIENCE, List.of("doswiadczenie", "historia zatrudnienia", "przebieg kariery",
                    "przebieg pracy", "kariera zawodowa", "zatrudnienie", "experience", "work experience",
                    "professional experience", "employment", "work history", "career history"),
            SectionType.SKILLS, List.of("umiejetnosci", "kompetencje", "technologie", "stack technologiczny",
                    "tech stack", "narzedzia", "skills", "technical skills", "key skills", "core skills",
                    "competencies", "technologies"),
            SectionType.EDUCATION, List.of("wyksztalcenie", "edukacja", "studia", "education", "academic"),
            SectionType.LANGUAGES, List.of("jezyki obce", "jezyki", "znajomosc jezykow", "languages", "language skills"),
            SectionType.PROJECTS, List.of("projekty", "wybrane projekty", "projects", "portfolio"),
            SectionType.CERTIFICATES, List.of("certyfikaty", "kursy", "szkolenia", "certyfikaty i kursy",
                    "certifications", "certificates", "courses", "trainings"),
            SectionType.INTERESTS, List.of("zainteresowania", "hobby", "interests", "pasje")));

    /** Linia tekstu (przycięta) z pozycją początku w oryginale. */
    record Line(int index, int start, String text, SectionType section, boolean heading, boolean bullet) {

        int end() {
            return start + text.length();
        }

        /** Treść bez wiodącego punktora. */
        String body() {
            int i = 0;
            while (i < text.length() && (BULLET_CHARS.indexOf(text.charAt(i)) >= 0 || text.charAt(i) == ' ')) {
                i++;
            }
            return text.substring(i);
        }
    }

    private final String text;
    private final List<Line> lines;
    private final Map<SectionType, Line> headings;

    private CvStructure(String text, List<Line> lines, Map<SectionType, Line> headings) {
        this.text = text;
        this.lines = lines;
        this.headings = headings;
    }

    static CvStructure parse(String source) {
        String text = source == null ? "" : source;
        List<Line> lines = new ArrayList<>();
        Map<SectionType, Line> headings = new EnumMap<>(SectionType.class);
        SectionType current = null;
        int pos = 0;
        int index = 0;
        while (pos <= text.length()) {
            int nl = text.indexOf('\n', pos);
            int lineEnd = nl < 0 ? text.length() : nl;
            String raw = text.substring(pos, lineEnd);
            int lead = 0;
            while (lead < raw.length() && Character.isWhitespace(raw.charAt(lead))) {
                lead++;
            }
            String stripped = raw.strip();
            if (!stripped.isEmpty()) {
                Heading heading = headingOf(stripped);
                if (heading != null) {
                    // etykieta z treścią w tej samej linii („Narzędzia: Jira”) nie zmienia bieżącej sekcji
                    if (!heading.inline()) {
                        current = heading.type();
                    }
                    Line l = new Line(index++, pos + lead, stripped, heading.type(), true, false);
                    headings.putIfAbsent(heading.type(), l);
                    lines.add(l);
                } else {
                    lines.add(new Line(index++, pos + lead, stripped, current, false, isBullet(stripped)));
                }
            }
            if (nl < 0) {
                break;
            }
            pos = nl + 1;
        }
        return new CvStructure(text, lines, headings);
    }

    /** Nagłówek sekcji; {@code inline} = etykieta z treścią w tej samej linii („Języki: angielski B2”). */
    record Heading(SectionType type, boolean inline) {
    }

    /** Typ sekcji, jeśli linia jest nagłówkiem; w przeciwnym razie null. */
    static SectionType headingType(String line) {
        Heading h = headingOf(line);
        return h == null ? null : h.type();
    }

    static Heading headingOf(String line) {
        String f = Text.fold(line).strip();
        // usuń ozdobniki na początku i końcu: „== DOŚWIADCZENIE ==”, „Umiejętności:”
        f = f.replaceAll("^[^\\p{L}]+", "").replaceAll("[^\\p{L})]+$", "");
        if (f.isEmpty()) {
            return null;
        }
        String original = Text.fold(line);
        for (var e : HEADINGS.entrySet()) {
            for (String kw : e.getValue()) {
                if (!f.startsWith(kw)) {
                    continue;
                }
                boolean wordEnd = f.length() == kw.length() || !Character.isLetter(f.charAt(kw.length()));
                if (!wordEnd) {
                    continue;
                }
                boolean shortLine = line.length() <= HEADING_MAX_CHARS && words(f) <= 4;
                int colon = original.indexOf(':');
                // „Umiejętności techniczne: Java, SQL” — etykieta z treścią w tej samej linii
                boolean labelled = colon > 0 && colon <= original.indexOf(kw) + kw.length() + 15
                        && !NO_LABEL.contains(kw) && hasContent(original.substring(colon + 1));
                if (labelled) {
                    return new Heading(e.getKey(), true);
                }
                if (shortLine) {
                    return new Heading(e.getKey(), false);
                }
            }
        }
        return null;
    }

    private static boolean hasContent(String s) {
        return s.chars().anyMatch(Character::isLetterOrDigit);
    }

    static boolean isBullet(String line) {
        if (line.isEmpty()) {
            return false;
        }
        char c = line.charAt(0);
        if (BULLET_CHARS.indexOf(c) >= 0) {
            // „-” musi być oddzielony spacją lub być znakiem punktora; „—” na początku też uznajemy
            return line.length() > 1 && (c != '-' || line.charAt(1) == ' ');
        }
        return NUMBERED.matcher(line).matches();
    }

    private static int words(String s) {
        int n = 0;
        var m = WORD.matcher(s);
        while (m.find()) {
            n++;
        }
        return n;
    }

    String text() {
        return text;
    }

    List<Line> lines() {
        return lines;
    }

    boolean hasSection(SectionType type) {
        return headings.containsKey(type);
    }

    Line heading(SectionType type) {
        return headings.get(type);
    }

    List<Line> linesIn(SectionType type) {
        return lines.stream().filter(l -> l.section() == type && !l.heading()).toList();
    }

    List<Line> bullets() {
        return lines.stream().filter(Line::bullet).toList();
    }
}

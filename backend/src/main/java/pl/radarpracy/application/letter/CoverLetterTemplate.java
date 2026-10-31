package pl.radarpracy.application.letter;

import pl.radarpracy.application.letter.CoverLetterResult.Evidence;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchLevel;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.RequirementMatch;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline'owy list motywacyjny składany z dosłownych cytatów CV (pomysł #42) — czysta logika.
 * Teksty polskie są neutralne płciowo („zgłaszam swoją kandydaturę”, „chętnie opowiem”).
 */
public final class CoverLetterTemplate {

    /** Najwięcej punktów-dowodów w liście. */
    static final int MAX_POINTS = 5;
    /** Minimalna długość fragmentu w cudzysłowie traktowanego jako cytat. */
    static final int MIN_QUOTE = 15;

    public enum Tone {
        FORMAL, CASUAL;

        /** „formalny” (domyślnie) albo „swobodny”; akceptuje też angielskie nazwy. */
        public static Tone parse(String s) {
            if (s == null || s.isBlank()) {
                return FORMAL;
            }
            return switch (Text.fold(s.strip())) {
                case "formalny", "formal" -> FORMAL;
                case "swobodny", "casual", "nieformalny" -> CASUAL;
                default -> throw new IllegalArgumentException("Nieznany ton listu: " + s + " (dostępne: formalny, swobodny)");
            };
        }

        public String label() {
            return this == FORMAL ? "formalny" : "swobodny";
        }
    }

    public enum Language {
        PL, EN;

        public static Language parse(String s) {
            if (s == null || s.isBlank()) {
                return PL;
            }
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "pl", "polski" -> PL;
                case "en", "angielski", "english" -> EN;
                default -> throw new IllegalArgumentException("Nieobsługiwany język listu: " + s + " (dostępne: pl, en)");
            };
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private CoverLetterTemplate() {
    }

    /** Gdy pełnych dowodów jest co najmniej tyle, list pomija wymagania spełnione częściowo. */
    static final int ENOUGH_FULL = 3;

    /**
     * Dowody do listu: wymagania spełnione w pełni lub częściowo, z cytatem, który jest dosłownym
     * fragmentem CV (sprawdzane ponownie tutaj). Najpierw pełne, potem obowiązkowe, potem według punktów.
     */
    public static List<Evidence> selectEvidence(MatchResult match, String cvText) {
        return selectEvidence(match, cvText, null);
    }

    /**
     * Jak {@link #selectEvidence(MatchResult, String)}, ale ze słownikiem umiejętności: zamiast pierwszego
     * trafienia (często nagłówka CV) wybiera linię CV, która najlepiej ilustruje wymaganie ({@link #bestLine}).
     * Cytat nadal jest dosłownym fragmentem CV.
     */
    public static List<Evidence> selectEvidence(MatchResult match, String cvText, SkillDictionary dictionary) {
        if (match == null || cvText == null || cvText.isBlank()) {
            return List.of();
        }
        return match.requirements().stream()
                .filter(r -> r.level() == MatchLevel.FULL || r.level() == MatchLevel.PARTIAL)
                .filter(r -> r.evidence() != null && r.evidenceVerified() && cvText.contains(r.evidence()))
                .sorted(Comparator.comparing((RequirementMatch r) -> r.level() != MatchLevel.FULL)
                        .thenComparing(r -> !r.mustHave())
                        .thenComparing(Comparator.comparingDouble(RequirementMatch::points).reversed()))
                .map(r -> new Evidence(r.requirement(), quoteFor(r, cvText, dictionary), r.mustHave(),
                        r.level() != MatchLevel.FULL))
                .toList();
    }

    private static String quoteFor(RequirementMatch r, String cvText, SkillDictionary dictionary) {
        if (dictionary == null || r.skills() == null || r.skills().isEmpty()) {
            return r.evidence();
        }
        String line = bestLine(cvText, r.skills(), dictionary);
        return line != null && cvText.contains(line) ? line : r.evidence();
    }

    /**
     * Linia CV najlepiej potwierdzająca umiejętności wymagania: +1 za każdą umiejętność z wymagania,
     * −0,1 za każdą inną (wyliczanki typu „Java, Spring, SQL, …” są mniej wymowne), +0,3 za liczby
     * (lata, wyniki). Pomijane są linie krótsze niż {@value #LINE_MIN} i dłuższe niż {@value #LINE_MAX} znaków.
     * Zwraca linię bez punktora (dosłowny fragment CV) albo null.
     */
    static String bestLine(String cvText, List<String> skills, SkillDictionary dictionary) {
        String best = null;
        double bestScore = 0;
        for (String raw : Text.lines(cvText)) {
            String line = stripBullet(raw);
            if (line.length() < LINE_MIN || line.length() > LINE_MAX) {
                continue;
            }
            var inLine = dictionary.skillsIn(line);
            long matched = skills.stream().filter(inLine::contains).count();
            if (matched == 0) {
                continue;
            }
            double score = matched - 0.1 * (inLine.size() - matched) + (line.matches(".*\\d.*") ? 0.3 : 0);
            if (score > bestScore + 1e-9 || (Math.abs(score - bestScore) < 1e-9 && best != null
                    && line.length() > best.length())) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    static final int LINE_MIN = 25;
    static final int LINE_MAX = 240;

    private static String stripBullet(String line) {
        int i = 0;
        while (i < line.length() && "-•*·▪–—>".indexOf(line.charAt(i)) >= 0) {
            i++;
        }
        return line.substring(i).strip();
    }

    /**
     * Punkty listu: wymagania z tym samym cytatem są łączone; maks. {@link #MAX_POINTS}. Wymagania spełnione
     * częściowo trafiają do listu tylko wtedy, gdy pełnych dowodów jest mniej niż {@link #ENOUGH_FULL}.
     */
    static Map<String, List<String>> points(List<Evidence> evidence) {
        boolean skipPartial = evidence.stream().filter(e -> !e.partial()).count() >= ENOUGH_FULL;
        Map<String, List<String>> byQuote = new LinkedHashMap<>();
        for (Evidence e : evidence) {
            if (skipPartial && e.partial()) {
                continue;
            }
            if (byQuote.size() >= MAX_POINTS && !byQuote.containsKey(e.quote())) {
                continue;
            }
            byQuote.computeIfAbsent(e.quote(), k -> new ArrayList<>()).add(e.requirement());
        }
        return byQuote;
    }

    /** Składa list z szablonu. */
    public static String compose(String title, String company, String fullName, List<Evidence> evidence, Tone tone,
                                 Language language) {
        String role = Text.isBlank(title) ? (language == Language.PL ? "[stanowisko]" : "[position]") : title.strip();
        String firm = Text.isBlank(company) ? null : company.strip();
        String name = Text.isBlank(fullName) ? (language == Language.PL ? "[Imię i nazwisko]" : "[Your name]")
                : fullName.strip();
        Map<String, List<String>> points = points(evidence);
        StringBuilder bullets = new StringBuilder();
        String open = language == Language.PL ? "„" : "\"";
        String close = language == Language.PL ? "”" : "\"";
        for (var e : points.entrySet()) {
            bullets.append("• ").append(String.join("; ", e.getValue())).append(": ")
                    .append(open).append(e.getKey()).append(close).append('\n');
        }
        boolean has = !points.isEmpty();
        StringBuilder sb = new StringBuilder();
        if (language == Language.PL && tone == Tone.FORMAL) {
            sb.append("Szanowni Państwo,\n\n");
            sb.append("zgłaszam swoją kandydaturę na stanowisko ").append(role)
                    .append(firm == null ? "" : " w firmie " + firm).append(". ");
            if (has) {
                sb.append("Poniżej zestawiam wymagania z ogłoszenia z fragmentami mojego CV, które je potwierdzają:\n\n")
                        .append(bullets).append('\n')
                        .append("Szczegóły mojego doświadczenia znajdą Państwo w załączonym CV. ");
            } else {
                sb.append("Moje doświadczenie i umiejętności opisuję w załączonym CV. ");
            }
            sb.append("Chętnie opowiem o nich więcej podczas rozmowy.\n\nZ wyrazami szacunku\n").append(name);
        } else if (language == Language.PL) {
            sb.append("Dzień dobry,\n\n");
            sb.append("chętnie dołączę do ").append(firm == null ? "Waszego zespołu" : "zespołu " + firm)
                    .append(" na stanowisku ").append(role).append(". ");
            if (has) {
                sb.append("Oto kilka konkretów z mojego CV, które odpowiadają Waszym wymaganiom:\n\n")
                        .append(bullets).append('\n')
                        .append("Więcej szczegółów znajdziecie w CV. ");
            } else {
                sb.append("Szczegóły mojego doświadczenia znajdziecie w CV. ");
            }
            sb.append("Chętnie porozmawiam o tym, jak mogę pomóc Waszemu zespołowi.\n\nPozdrawiam serdecznie\n")
                    .append(name);
        } else if (tone == Tone.FORMAL) {
            sb.append("Dear Hiring Team,\n\n");
            sb.append("I am applying for the ").append(role).append(" position")
                    .append(firm == null ? "" : " at " + firm).append(". ");
            if (has) {
                sb.append("Below I have matched the requirements from your job posting with excerpts from my CV "
                                + "that confirm them:\n\n")
                        .append(bullets).append('\n')
                        .append("You will find more details in the attached CV. ");
            } else {
                sb.append("My experience and skills are described in the attached CV. ");
            }
            sb.append("I would be glad to discuss my experience in an interview.\n\nYours faithfully,\n").append(name);
        } else {
            sb.append(firm == null ? "Hi there,\n\n" : "Hi " + firm + " team,\n\n");
            sb.append("I'd love to join you as ").append(role).append(". ");
            if (has) {
                sb.append("Here are a few highlights from my CV that match what you're looking for:\n\n")
                        .append(bullets).append('\n')
                        .append("There's more in my CV. ");
            } else {
                sb.append("You'll find the details in my CV. ");
            }
            sb.append("Happy to chat about how I can help your team.\n\nBest regards,\n").append(name);
        }
        return sb.toString();
    }

    /**
     * Fragmenty w cudzysłowie (≥ 15 znaków), których nie ma dosłownie w żadnym ze źródeł — używane do
     * odrzucenia odpowiedzi AI, która „cytuje” coś spoza CV i ogłoszenia.
     */
    static List<String> unverifiedQuotes(String text, String... sources) {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text;
        int i = 0;
        while (i < t.length()) {
            String closers = switch (t.charAt(i)) {
                case '\u201E' -> "\u201D\u201C\""; // „ … ” / “ / "
                case '\u201C' -> "\u201D\"";         // “ … ” / "
                case '"' -> "\"";
                default -> null;
            };
            if (closers == null) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < t.length() && closers.indexOf(t.charAt(j)) < 0) {
                j++;
            }
            if (j >= t.length()) {
                break;
            }
            String q = t.substring(i + 1, j).strip();
            if (q.length() >= MIN_QUOTE && !inAny(q, sources)) {
                out.add(q);
            }
            i = j + 1;
        }
        return out;
    }

    private static boolean inAny(String q, String... sources) {
        for (String s : sources) {
            if (s != null && s.contains(q)) {
                return true;
            }
        }
        return false;
    }
}

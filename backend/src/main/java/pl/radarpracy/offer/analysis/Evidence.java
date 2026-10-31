package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyszukiwanie wzorców w treści ogłoszenia z cytatem-dowodem.
 *
 * <p>Wzorce działają na tekście „złożonym” ({@link Text#fold(String)} — małe litery, bez polskich
 * znaków), który ma tę samą długość co oryginał, więc indeksy wskazują te same miejsca w oryginale.
 * Cytat to zawsze dosłowny fragment źródła (linia lub zdanie z dopasowaniem) — sprawdzane w
 * {@link #quote(String, int, int)}.</p>
 */
final class Evidence {

    /** Maksymalna długość cytatu. */
    static final int MAX_QUOTE = 220;

    private Evidence() {
    }

    /** Dopasowanie: pozycja w oryginale, dosłowny cytat oraz linia (złożona) z dopasowaniem. */
    record Hit(int start, int end, String quote, String foldedLine) {
    }

    static Optional<Hit> find(String original, Pattern pattern) {
        List<Hit> all = findAll(original, pattern, 1);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    static List<Hit> findAll(String original, Pattern pattern, int limit) {
        List<Hit> out = new ArrayList<>();
        if (Text.isBlank(original)) {
            return out;
        }
        String folded = Text.fold(original);
        Matcher m = pattern.matcher(folded);
        while (m.find() && out.size() < limit) {
            if (m.end() == m.start()) {
                continue;
            }
            out.add(new Hit(m.start(), m.end(), quote(original, m.start(), m.end()),
                    lineAround(folded, m.start(), m.end())));
        }
        return out;
    }

    /**
     * Cytat wokół przedziału [start, end): cała linia, a gdy jest za długa — zdanie (granice
     * „. ”, „! ”, „? ”, „; ”) lub okno wokół dopasowania. Wynik jest zawsze podciągiem źródła.
     */
    static String quote(String source, int start, int end) {
        int lineFrom = source.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        if (start == 0) {
            lineFrom = 0;
        }
        int lineTo = source.indexOf('\n', end);
        if (lineTo < 0) {
            lineTo = source.length();
        }
        int from = lineFrom;
        int to = lineTo;
        if (to - from > MAX_QUOTE) {
            int s = sentenceStart(source, start, lineFrom);
            int e = sentenceEnd(source, end, lineTo);
            from = s;
            to = e;
            if (to - from > MAX_QUOTE) {
                from = Math.max(from, start - MAX_QUOTE / 3);
                to = Math.min(to, Math.max(end, from + MAX_QUOTE));
            }
        }
        String q = stripDecorations(source.substring(from, to));
        if (q.isEmpty() || !source.contains(q)) {
            return source.substring(start, end).strip();
        }
        return q;
    }

    /** Czy cytat jest dosłownym fragmentem źródła (zasada nr 2). */
    static boolean isVerbatim(String source, String quote) {
        return quote != null && !quote.isBlank() && source != null && source.contains(quote);
    }

    /** Linia (w tekście złożonym) zawierająca przedział. */
    static String lineAround(String folded, int start, int end) {
        int from = start == 0 ? 0 : folded.lastIndexOf('\n', start - 1) + 1;
        int to = folded.indexOf('\n', end);
        return folded.substring(from, to < 0 ? folded.length() : to);
    }

    private static int sentenceStart(String s, int start, int min) {
        for (int i = start - 1; i > min; i--) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == ';') && i + 1 < s.length() && Character.isWhitespace(s.charAt(i + 1))
                    && !isAbbreviation(s, i)) {
                return i + 1;
            }
        }
        return min;
    }

    private static int sentenceEnd(String s, int end, int max) {
        for (int i = end; i < max; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == ';') && (i + 1 >= s.length() || Character.isWhitespace(s.charAt(i + 1)))
                    && !isAbbreviation(s, i)) {
                return i + 1;
            }
        }
        return max;
    }

    /** „min.”, „np.”, „ok.”, „tj.”, „m.in.”, „sp.” — kropka nie kończy zdania. */
    private static boolean isAbbreviation(String s, int dot) {
        if (s.charAt(dot) != '.') {
            return false;
        }
        int i = dot - 1;
        while (i >= 0 && Character.isLetter(s.charAt(i))) {
            i--;
        }
        String word = Text.fold(s.substring(i + 1, dot));
        return word.length() <= 3 || List.of("min", "max", "ok", "np", "tj", "itp", "itd", "tzw", "godz", "tys").contains(word);
    }

    /** Usuwa białe znaki i wiodące punktory/znaczniki Markdown, zachowując podciąg źródła. */
    static String stripDecorations(String s) {
        String t = s.strip();
        int lead = 0;
        while (lead < t.length() && "-•*·▪–—>#✓✔►".indexOf(t.charAt(lead)) >= 0) {
            lead++;
        }
        return t.substring(lead).strip();
    }

    /** Skompilowany wzorzec do użycia na tekście złożonym. */
    static Pattern p(String regex) {
        return Pattern.compile(regex);
    }
}

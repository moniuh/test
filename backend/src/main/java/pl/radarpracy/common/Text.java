package pl.radarpracy.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Narzędzia tekstowe wspólne dla modułów.
 *
 * <p>{@link #fold(String)} zachowuje długość tekstu (mapowanie znak→znak), więc indeksy
 * znalezione w tekście „złożonym” wskazują te same miejsca w oryginale. Dzięki temu cytaty
 * (dowody) zawsze są dosłownymi fragmentami źródła.</p>
 */
public final class Text {

    private static final String PL_FROM = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ";
    private static final String PL_TO = "acelnoszzACELNOSZZ";
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}+#.]+");
    private static final Set<String> STOPWORDS = Set.of(
            "oraz", "albo", "lub", "the", "and", "with", "for", "your", "you", "our", "will",
            "jest", "się", "sie", "dla", "nas", "naszego", "naszej", "który", "ktory", "która", "ktora",
            "które", "ktore", "przy", "jako", "this", "that", "from", "have", "has", "are", "być", "byc",
            "bardzo", "dobra", "dobry", "dobre", "mile", "widziane", "widziana", "wymagane", "wymagana",
            "znajomość", "znajomosc", "umiejętność", "umiejetnosc", "experience", "knowledge", "skills",
            "praca", "pracy", "pracę", "prace", "of", "in", "to", "a", "an", "or", "na", "w", "z", "i", "do",
            "od", "po", "we", "is", "be", "as", "at", "co", "najmniej", "min", "least", "years", "lat", "lata");

    private Text() {
    }

    /** Małe litery i usunięte polskie znaki diakrytyczne; długość zachowana. */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int idx = PL_FROM.indexOf(c);
            if (idx >= 0) {
                c = PL_TO.charAt(idx);
            }
            char lower = Character.toLowerCase(c);
            sb.append(lower);
        }
        return sb.toString();
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Linie tekstu bez pustych, przycięte. */
    public static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String line : s.split("\\R")) {
            String t = line.strip();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Tokeny (słowa) w postaci złożonej, bez słów nieistotnych i krótszych niż {@code minLength}. */
    public static List<String> tokens(String s, int minLength) {
        List<String> out = new ArrayList<>();
        var m = TOKEN.matcher(fold(s));
        while (m.find()) {
            String t = m.group();
            while (t.endsWith(".")) {
                t = t.substring(0, t.length() - 1);
            }
            if (t.length() >= minLength && !STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Uproszczony „rdzeń” słowa dla polskiej fleksji: pierwsze {@code n} znaków. */
    public static String stem(String token, int n) {
        return token.length() <= n ? token : token.substring(0, n);
    }

    /**
     * Zwraca zdanie lub linię źródła zawierającą przedział [start, end). Wynik jest zawsze
     * dosłownym podciągiem {@code source} (przycinamy tylko białe znaki i skracamy do ~maxLen).
     */
    public static String quoteAround(String source, int start, int end, int maxLen) {
        int from = start;
        while (from > 0 && !isBoundary(source.charAt(from - 1))) {
            from--;
        }
        int to = end;
        while (to < source.length() && !isBoundary(source.charAt(to))) {
            to++;
        }
        if (to - from > maxLen) {
            int half = maxLen / 2;
            from = Math.max(from, start - half);
            to = Math.min(to, Math.max(end, from + maxLen));
        }
        String quote = source.substring(from, to);
        String stripped = quote.strip();
        // usuń wiodące punktory, ale nadal jako podciąg źródła
        int lead = 0;
        while (lead < stripped.length() && "-•*·▪–—>".indexOf(stripped.charAt(lead)) >= 0) {
            lead++;
        }
        return stripped.substring(lead).strip();
    }

    private static boolean isBoundary(char c) {
        return c == '\n' || c == '\r' || c == '.' || c == ';' || c == '!' || c == '?' || c == '•';
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String capitalize(String s) {
        if (isBlank(s)) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}

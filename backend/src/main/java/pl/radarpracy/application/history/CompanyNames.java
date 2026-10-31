package pl.radarpracy.application.history;

import pl.radarpracy.common.Text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizacja nazw firm i stanowisk do wykrywania ponownej aplikacji (pomysł #47) — czysta logika.
 */
public final class CompanyNames {

    private static final Pattern PUNCT = Pattern.compile("[^\\p{L}\\p{N}]+");
    /** Formy prawne (po usunięciu interpunkcji), usuwane z końca nazwy. */
    private static final List<String> LEGAL_SUFFIXES = List.of(
            "spolka z ograniczona odpowiedzialnoscia", "spolka akcyjna", "spolka jawna", "spolka komandytowa",
            "spolka cywilna", "sp z o o", "sp z oo", "sp zoo", "s a", "sa", "sp j", "sp k", "sp p", "s c",
            "sp z o o sp k", "sp", "ltd", "limited", "gmbh", "inc", "llc", "plc", "ab", "as", "oy", "bv", "ag",
            "s r o", "corp", "co");
    /** Słowa pomijane przy porównaniu stanowisk: poziomy, tryby pracy, oznaczenia płci. */
    private static final Set<String> ROLE_NOISE = new HashSet<>(Arrays.asList(
            "junior", "mid", "regular", "senior", "lead", "principal", "staff", "medior", "intern", "stazysta",
            "mlodszy", "mlodsza", "starszy", "starsza", "remote", "zdalnie", "zdalna", "hybrid", "hybrydowo",
            "eu", "pl", "k", "m", "x", "f", "w", "z", "i", "the", "and", "of", "level", "ii", "iii", "100"));
    static final double ROLE_THRESHOLD = 0.6;

    private CompanyNames() {
    }

    /** Małe litery, bez polskich znaków, interpunkcji i form prawnych („sp. z o.o.”, „S.A.”, „Ltd” …). */
    public static String normalize(String company) {
        if (company == null) {
            return "";
        }
        String s = PUNCT.matcher(Text.fold(company)).replaceAll(" ").strip();
        boolean changed = true;
        while (changed && !s.isEmpty()) {
            changed = false;
            for (String suffix : LEGAL_SUFFIXES) {
                if (s.equals(suffix)) {
                    break;
                }
                if (s.endsWith(" " + suffix)) {
                    s = s.substring(0, s.length() - suffix.length()).strip();
                    changed = true;
                    break;
                }
            }
        }
        return s;
    }

    /** Czy dwie nazwy firm oznaczają tę samą firmę (po normalizacji). */
    public static boolean sameCompany(String a, String b) {
        String na = normalize(a);
        return !na.isEmpty() && na.equals(normalize(b));
    }

    /** Istotne słowa tytułu stanowiska. */
    static Set<String> roleTokens(String title) {
        Set<String> out = new HashSet<>();
        for (String t : PUNCT.matcher(Text.fold(title == null ? "" : title)).replaceAll(" ").split(" ")) {
            if (!t.isBlank() && !ROLE_NOISE.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Podobieństwo stanowisk (współczynnik Jaccarda na istotnych słowach), 0–1. */
    public static double roleSimilarity(String a, String b) {
        Set<String> ta = roleTokens(a);
        Set<String> tb = roleTokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(ta);
        common.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) common.size() / union.size();
    }

    public static boolean similarRole(String a, String b) {
        return roleSimilarity(a, b) >= ROLE_THRESHOLD;
    }
}

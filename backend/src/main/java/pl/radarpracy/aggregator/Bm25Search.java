package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyszukiwanie pełnotekstowe BM25F (pomysł #61) po polach z wagami: tytuł ×3, firma ×2, umiejętności ze
 * słownika ×2, treść ×1. Tokeny są składane ({@link Text#fold} — bez polskich znaków) i skracane do
 * prostego rdzenia ({@link Text#stem}), więc „księgowa” znajdzie „Księgowy” i „ksiegowosc”.
 * Składnia zapytania: słowa, frazy w cudzysłowie („"spring boot"”, wymagane), wykluczenia „-słowo”
 * i „-"fraza"”.
 */
public final class Bm25Search {

    static final double K1 = 1.2;
    static final double B = 0.75;
    static final int STEM = 6;
    private static final Pattern QUERY_PART = Pattern.compile("(-?)\"([^\"]*)\"|(\\S+)");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}+#.]+");

    /** Pola dokumentu i ich wagi. */
    public enum Field {
        TITLE(3.0, "tytuł"), COMPANY(2.0, "firma"), SKILLS(2.0, "umiejętności"), BODY(1.0, "treść");

        final double weight;
        final String label;

        Field(double weight, String label) {
            this.weight = weight;
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Dokument do przeszukania (skills = nazwy umiejętności ze słownika, rozdzielone spacjami). */
    public record Doc(long id, String title, String company, String skills, String body) {

        String field(Field f) {
            return switch (f) {
                case TITLE -> title;
                case COMPANY -> company;
                case SKILLS -> skills;
                case BODY -> body;
            };
        }
    }

    /**
     * Rozbiór zapytania.
     *
     * @param terms           rdzenie słów do rankingu (także z fraz)
     * @param phrases         wymagane frazy (znormalizowane)
     * @param excludedTerms   rdzenie wykluczonych słów
     * @param excludedPhrases wykluczone frazy
     */
    public record Query(List<String> terms, List<String> phrases, List<String> excludedTerms,
                        List<String> excludedPhrases) {

        public boolean isEmpty() {
            return terms.isEmpty() && phrases.isEmpty();
        }
    }

    /** Trafienie: wynik i pola, w których wystąpiły słowa zapytania. */
    public record Hit(long id, double score, List<Field> fields) {
    }

    private Bm25Search() {
    }

    public static Query parse(String q) {
        Set<String> terms = new LinkedHashSet<>();
        List<String> phrases = new ArrayList<>();
        Set<String> excluded = new LinkedHashSet<>();
        List<String> excludedPhrases = new ArrayList<>();
        Matcher m = QUERY_PART.matcher(Text.nullToEmpty(q));
        while (m.find()) {
            if (m.group(2) != null) {
                boolean negative = !m.group(1).isEmpty();
                String phrase = normalizePhrase(m.group(2));
                if (phrase.isEmpty()) {
                    continue;
                }
                if (negative) {
                    excludedPhrases.add(phrase);
                } else {
                    phrases.add(phrase);
                    terms.addAll(analyze(m.group(2)));
                }
            } else {
                String word = m.group(3);
                if (word.startsWith("-") && word.length() > 1) {
                    excluded.addAll(analyze(word.substring(1)));
                } else {
                    terms.addAll(analyze(word));
                }
            }
        }
        terms.removeAll(excluded);
        return new Query(List.copyOf(terms), List.copyOf(phrases), List.copyOf(excluded), List.copyOf(excludedPhrases));
    }

    /** Tokeny złożone, bez słów nieistotnych, skrócone do rdzenia. */
    public static List<String> analyze(String text) {
        List<String> out = new ArrayList<>();
        for (String t : Text.tokens(text, 1)) {
            if (t.length() == 1 && !Character.isDigit(t.charAt(0)) && !t.equals("c") && !t.equals("r")) {
                continue;
            }
            out.add(Text.stem(t, STEM));
        }
        return out;
    }

    /** Fraza: złożona, bez interpunkcji, pojedyncze spacje. */
    static String normalizePhrase(String s) {
        return Text.fold(s).replaceAll("[^\\p{L}\\p{N}+#]+", " ").strip();
    }

    public static List<Hit> search(List<Doc> docs, Query q) {
        if (q.isEmpty() || docs.isEmpty()) {
            return List.of();
        }
        int n = docs.size();
        Field[] fields = Field.values();
        // analiza dokumentów: tf per pole
        List<Map<Field, Map<String, Integer>>> tfs = new ArrayList<>(n);
        List<Map<Field, Integer>> lens = new ArrayList<>(n);
        Map<Field, Double> totalLen = new HashMap<>();
        Map<String, Integer> df = new HashMap<>();
        for (Doc d : docs) {
            Map<Field, Map<String, Integer>> perField = new LinkedHashMap<>();
            Map<Field, Integer> len = new HashMap<>();
            Set<String> seen = new java.util.HashSet<>();
            for (Field f : fields) {
                Map<String, Integer> tf = new HashMap<>();
                List<String> tokens = analyze(d.field(f));
                for (String t : tokens) {
                    tf.merge(t, 1, Integer::sum);
                    seen.add(t);
                }
                perField.put(f, tf);
                len.put(f, tokens.size());
                totalLen.merge(f, (double) tokens.size(), Double::sum);
            }
            for (String t : seen) {
                df.merge(t, 1, Integer::sum);
            }
            tfs.add(perField);
            lens.add(len);
        }
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Doc d = docs.get(i);
            Map<Field, Map<String, Integer>> perField = tfs.get(i);
            if (excluded(d, perField, q)) {
                continue;
            }
            if (!q.phrases().isEmpty()) {
                String all = " " + normalizePhrase(d.title() + " " + Text.nullToEmpty(d.company()) + " "
                        + Text.nullToEmpty(d.body())) + " ";
                boolean ok = q.phrases().stream().allMatch(p -> all.contains(" " + p + " "));
                if (!ok) {
                    continue;
                }
            }
            double score = 0;
            Set<Field> matched = new LinkedHashSet<>();
            for (String t : q.terms()) {
                double tfTilde = 0;
                for (Field f : fields) {
                    int tf = perField.get(f).getOrDefault(t, 0);
                    if (tf == 0) {
                        continue;
                    }
                    matched.add(f);
                    double avg = totalLen.getOrDefault(f, 0.0) / n;
                    double norm = avg <= 0 ? 1.0 : (1 - B + B * lens.get(i).get(f) / avg);
                    tfTilde += f.weight * tf / norm;
                }
                if (tfTilde > 0) {
                    int dft = df.getOrDefault(t, 0);
                    double idf = Math.log(1 + (n - dft + 0.5) / (dft + 0.5));
                    score += idf * tfTilde * (K1 + 1) / (tfTilde + K1);
                }
            }
            if (score > 0 || (!q.phrases().isEmpty() && q.terms().isEmpty())) {
                hits.add(new Hit(d.id(), score, List.copyOf(matched)));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()) != 0 ? Double.compare(b.score(), a.score())
                : Long.compare(b.id(), a.id()));
        return hits;
    }

    private static boolean excluded(Doc d, Map<Field, Map<String, Integer>> perField, Query q) {
        for (String t : q.excludedTerms()) {
            for (Map<String, Integer> tf : perField.values()) {
                if (tf.containsKey(t)) {
                    return true;
                }
            }
        }
        if (!q.excludedPhrases().isEmpty()) {
            String all = " " + normalizePhrase(d.title() + " " + Text.nullToEmpty(d.company()) + " "
                    + Text.nullToEmpty(d.body())) + " ";
            for (String p : q.excludedPhrases()) {
                if (all.contains(" " + p + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fragment treści wokół pierwszego dopasowania (dosłowny wycinek źródła) i zakresy do wyróżnienia.
     *
     * @param snippet    fragment (z „…” na brzegach, gdy wycięty ze środka)
     * @param highlights pary [początek, koniec) w {@code snippet}
     */
    public record Snippet(String snippet, List<int[]> highlights) {
    }

    public static Snippet snippet(String body, Query q, int maxLen) {
        String text = Text.nullToEmpty(body);
        if (text.isBlank()) {
            return new Snippet("", List.of());
        }
        Set<String> wanted = new LinkedHashSet<>(q.terms());
        String folded = Text.fold(text);
        int first = -1;
        Matcher m = TOKEN.matcher(folded);
        while (m.find()) {
            String tok = m.group();
            while (tok.endsWith(".")) {
                tok = tok.substring(0, tok.length() - 1);
            }
            if (!tok.isEmpty() && wanted.contains(Text.stem(tok, STEM))) {
                first = m.start();
                break;
            }
        }
        int start;
        int end;
        if (first < 0) {
            start = 0;
            end = Math.min(text.length(), maxLen);
        } else {
            start = Math.max(0, first - maxLen / 3);
            while (start > 0 && start < first && !Character.isWhitespace(text.charAt(start - 1))) {
                start++;
            }
            end = Math.min(text.length(), start + maxLen);
            while (end < text.length() && end > first && !Character.isWhitespace(text.charAt(end))) {
                end--;
            }
        }
        // białe znaki zwinięte do pojedynczych spacji; poza tym dosłowny wycinek treści
        String core = text.substring(start, end).replaceAll("\\s+", " ");
        String prefix = start > 0 ? "…" : "";
        String suffix = end < text.length() ? "…" : "";
        String snippet = prefix + core + suffix;
        List<int[]> highlights = new ArrayList<>();
        Matcher hm = TOKEN.matcher(Text.fold(snippet));
        while (hm.find()) {
            String tok = hm.group();
            int tokEnd = hm.end();
            while (tok.endsWith(".")) {
                tok = tok.substring(0, tok.length() - 1);
                tokEnd--;
            }
            if (!tok.isEmpty() && wanted.contains(Text.stem(tok, STEM))) {
                highlights.add(new int[]{hm.start(), tokEnd});
            }
        }
        return new Snippet(snippet, highlights);
    }
}

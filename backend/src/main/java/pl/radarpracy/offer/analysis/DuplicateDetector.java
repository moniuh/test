package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Podobieństwo dwóch ofert (pomysł #16) — liczone deterministycznie i jawnie:
 *
 * <ul>
 *   <li><b>firma</b> (waga {@value #W_COMPANY}): nazwa znormalizowana (bez form prawnych typu „sp. z o.o.”,
 *       „S.A.”, „GmbH”) — równe = 1, jedna zawiera drugą = 0,9, w innym razie Jaccard słów / Dice bigramów;</li>
 *   <li><b>tytuł</b> (waga {@value #W_TITLE}): Jaccard słów po usunięciu poziomu („Mid”, „Senior”) i oznaczeń „(k/m)”,
 *       z karą ×0,6, gdy oba tytuły podają różny poziom;</li>
 *   <li><b>treść</b> (waga {@value #W_CONTENT}): cosinus wektorów częstości „rdzeni” słów (6 pierwszych liter).</li>
 * </ul>
 *
 * <p>Brakujące składniki są pomijane, a wagi normalizowane. Różne firmy (podobieństwo nazw &lt; 0,3) → wynik × 0,8.
 * Ten sam adres ogłoszenia = 1,0. Prawie identyczna
 * treść (≥ 0,9) przy podobnym tytule podnosi wynik nawet przy innej nazwie firmy (np. agencja i pracodawca).</p>
 */
public final class DuplicateDetector {

    /** Od tego wyniku uznajemy ofertę za prawdopodobny duplikat. */
    public static final double THRESHOLD = 0.75;
    /** Od tego wyniku pokazujemy ofertę jako podobną. */
    public static final double MIN_SCORE = 0.5;

    static final double W_COMPANY = 0.35;
    static final double W_TITLE = 0.30;
    static final double W_CONTENT = 0.35;
    /** Poniżej tego podobieństwa nazw firmy uznajemy za różne… */
    static final double DIFFERENT_COMPANY = 0.3;
    /** …i mnożymy wynik przez ten współczynnik (chyba że treść jest prawie identyczna). */
    static final double DIFFERENT_COMPANY_FACTOR = 0.8;

    private static final Pattern LEGAL_PHRASES = Pattern.compile(
            "spolka z ograniczona odpowiedzialnoscia|spolka (?:akcyjna|jawna|komandytowa|komandytowo-akcyjna|cywilna"
                    + "|partnerska)|sp\\.?\\s*z\\s*o\\.?\\s*o\\.?|\\bsp\\.?\\s*[jkp]\\.?(?=\\s|$|,)|\\bs\\.\\s*a\\.?(?=\\s|$|,)"
                    + "|\\bs\\.r\\.o\\.?|\\bb\\.v\\.?");
    private static final Set<String> LEGAL_TOKENS = Set.of(
            "sa", "gmbh", "ltd", "limited", "inc", "llc", "ab", "ag", "bv", "sro", "plc", "corp", "corporation", "oy",
            "as", "nv", "sas", "sarl", "spa", "polska", "poland", "pl", "group", "grupa", "spolka", "sp", "zoo");
    private static final Pattern GENDER_MARK = Pattern.compile("\\(?\\b(?:k/m|m/k|m/f|f/m|m/f/d|f/m/d|k/m/n|m/k/n|m/w/d)\\b\\)?");
    private static final Map<String, String> LEVEL_WORDS = new HashMap<>();
    private static final Map<String, String> TITLE_SYNONYMS = Map.ofEntries(
            Map.entry("programista", "developer"), Map.entry("programistka", "developer"),
            Map.entry("deweloper", "developer"), Map.entry("developerka", "developer"), Map.entry("dev", "developer"),
            Map.entry("inzynier", "engineer"), Map.entry("inzynierka", "engineer"),
            Map.entry("full-stack", "fullstack"), Map.entry("front-end", "frontend"), Map.entry("back-end", "backend"),
            Map.entry("specjalistka", "specjalista"), Map.entry("specialist", "specjalista"),
            Map.entry("analityczka", "analityk"), Map.entry("analyst", "analityk"), Map.entry("testerka", "tester"));

    static {
        for (String w : List.of("junior", "jr", "mlodszy", "mlodsza")) {
            LEVEL_WORDS.put(w, "JUNIOR");
        }
        for (String w : List.of("mid", "regular", "medior")) {
            LEVEL_WORDS.put(w, "MID");
        }
        for (String w : List.of("senior", "sr", "starszy", "starsza")) {
            LEVEL_WORDS.put(w, "SENIOR");
        }
        for (String w : List.of("intern", "stazysta", "stazystka", "trainee")) {
            LEVEL_WORDS.put(w, "INTERN");
        }
    }

    private DuplicateDetector() {
    }

    /** Oferta sprowadzona do postaci porównywalnej (liczona raz na porównanie wielu par). */
    public record Fingerprint(Long id, String company, Set<String> companyTokens, Set<String> titleTokens,
                              String titleLevel, Map<String, Integer> terms, String url) {
    }

    /** Wynik porównania z jawnymi składnikami (null = składnik nieznany). */
    public record Similarity(double score, Double company, Double title, Double content, boolean sameUrl,
                             List<String> reasons) {
    }

    public static Fingerprint fingerprint(Long id, String title, String company, String description, String url) {
        String normCompany = normalizeCompany(company);
        Set<String> companyTokens = normCompany.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(normCompany.split(" ")));
        String level = null;
        Set<String> titleTokens = new LinkedHashSet<>();
        String ft = GENDER_MARK.matcher(Text.fold(Text.nullToEmpty(title))).replaceAll(" ");
        for (String tok : ft.split("[^\\p{L}\\p{N}+#-]+")) {
            String t = tok.strip();
            if (t.isEmpty() || t.equals("-")) {
                continue;
            }
            if (LEVEL_WORDS.containsKey(t)) {
                level = level == null ? LEVEL_WORDS.get(t) : level;
                continue;
            }
            titleTokens.add(TITLE_SYNONYMS.getOrDefault(t, t));
        }
        Map<String, Integer> terms = new HashMap<>();
        for (String tok : Text.tokens(description, 3)) {
            if (tok.chars().allMatch(Character::isDigit)) {
                continue;
            }
            terms.merge(Text.stem(tok, 6), 1, Integer::sum);
        }
        return new Fingerprint(id, normCompany, companyTokens, titleTokens, level, terms, normalizeUrl(url));
    }

    /** Nazwa firmy bez form prawnych, znaków przestankowych i polskich znaków. */
    public static String normalizeCompany(String company) {
        if (Text.isBlank(company)) {
            return "";
        }
        String f = LEGAL_PHRASES.matcher(Text.fold(company)).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        for (String t : f.split("[^\\p{L}\\p{N}]+")) {
            if (!t.isBlank() && !LEGAL_TOKENS.contains(t)) {
                tokens.add(t);
            }
        }
        return String.join(" ", tokens);
    }

    static String normalizeUrl(String url) {
        if (Text.isBlank(url)) {
            return null;
        }
        try {
            URI u = URI.create(url.strip());
            if (u.getHost() == null) {
                return null;
            }
            String host = u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
            return host + path;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Similarity compare(Fingerprint a, Fingerprint b) {
        List<String> reasons = new ArrayList<>();
        boolean sameUrl = a.url() != null && a.url().equals(b.url());
        Double company = companySimilarity(a, b);
        Double title = titleSimilarity(a, b);
        Double content = cosine(a.terms(), b.terms());

        double sum = 0;
        double weights = 0;
        if (company != null) {
            sum += W_COMPANY * company;
            weights += W_COMPANY;
        }
        if (title != null) {
            sum += W_TITLE * title;
            weights += W_TITLE;
        }
        if (content != null) {
            sum += W_CONTENT * content;
            weights += W_CONTENT;
        }
        double score = weights == 0 ? 0 : sum / weights;
        if (company != null && company < DIFFERENT_COMPANY) {
            score *= DIFFERENT_COMPANY_FACTOR;
        }
        if (content != null && title != null && content >= 0.9 && title >= 0.6) {
            score = Math.max(score, content * 0.95);
            reasons.add("prawie identyczna treść ogłoszenia");
        }
        if (sameUrl) {
            score = 1.0;
            reasons.add("ten sam adres ogłoszenia");
        }
        if (company != null) {
            reasons.add(company >= 0.99 ? "ta sama firma" : company >= 0.8 ? "bardzo podobna nazwa firmy"
                    : company < 0.3 ? "inna firma" : "częściowo podobna nazwa firmy");
        }
        if (title != null) {
            reasons.add(title >= 0.99 ? "ten sam tytuł stanowiska" : title >= 0.6 ? "podobny tytuł stanowiska"
                    : "inny tytuł stanowiska");
            if (a.titleLevel() != null && b.titleLevel() != null && !a.titleLevel().equals(b.titleLevel())) {
                reasons.add("różny poziom w tytule");
            }
        }
        if (content != null) {
            reasons.add("podobieństwo treści " + Math.round(content * 100) + "%");
        }
        return new Similarity(round(score), round(company), round(title), round(content), sameUrl, List.copyOf(reasons));
    }

    private static Double companySimilarity(Fingerprint a, Fingerprint b) {
        if (a.company().isEmpty() || b.company().isEmpty()) {
            return null;
        }
        if (a.company().equals(b.company())) {
            return 1.0;
        }
        String ca = a.company().replace(" ", "");
        String cb = b.company().replace(" ", "");
        if (ca.equals(cb)) {
            return 1.0;
        }
        String shorter = ca.length() <= cb.length() ? ca : cb;
        String longer = ca.length() <= cb.length() ? cb : ca;
        if (shorter.length() >= 3 && longer.startsWith(shorter)) {
            return 0.9;
        }
        double jaccard = jaccard(a.companyTokens(), b.companyTokens());
        double dice = dice(ca, cb);
        return Math.max(jaccard, dice * 0.9);
    }

    private static Double titleSimilarity(Fingerprint a, Fingerprint b) {
        if (a.titleTokens().isEmpty() || b.titleTokens().isEmpty()) {
            return null;
        }
        double j = jaccard(a.titleTokens(), b.titleTokens());
        if (a.titleLevel() != null && b.titleLevel() != null && !a.titleLevel().equals(b.titleLevel())) {
            j *= 0.6;
        }
        return j;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    /** Współczynnik Dice'a dla bigramów znaków — odporny na literówki („Logitech” / „LogiTec”). */
    static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 1 : 0;
        }
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int common = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer c = grams.get(g);
            if (c != null && c > 0) {
                common++;
                grams.put(g, c - 1);
            }
        }
        return 2.0 * common / ((a.length() - 1) + (b.length() - 1));
    }

    static Double cosine(Map<String, Integer> a, Map<String, Integer> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return null;
        }
        double dot = 0;
        for (var e : a.entrySet()) {
            Integer o = b.get(e.getKey());
            if (o != null) {
                dot += e.getValue() * o;
            }
        }
        double na = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double nb = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return na == 0 || nb == 0 ? null : dot / (na * nb);
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 100) / 100.0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

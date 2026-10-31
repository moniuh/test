package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Klucze deduplikacji w agregatorze (pomysł #49): znormalizowany adres URL oraz skrót
 * znormalizowanego „tytuł + firma” (bez form prawnych, oznaczeń płci, znaków interpunkcyjnych).
 */
public final class DedupKeys {

    private static final Pattern GENDER = Pattern.compile("\\(\\s*[mkfx]\\s*/\\s*[mkfx](\\s*/\\s*[mkfxd])?\\s*\\)");
    private static final Pattern LEGAL_FORM = Pattern.compile(
            "\\b(sp\\.?\\s*z\\s*o\\.?\\s*o\\.?|spolka\\s+z\\s+ograniczona\\s+odpowiedzialnoscia|spolka\\s+akcyjna"
                    + "|sp\\.?\\s*k\\.?|sp\\.?\\s*j\\.?|s\\.?\\s*a\\.?|s\\.?\\s*c\\.?|ltd\\.?|inc\\.?|gmbh|llc|plc|corp\\.?|ab)(?=\\s|$|[,.])");
    private static final Pattern TRACKING = Pattern.compile("(?i)^(utm_[a-z]+|fbclid|gclid|mc_[a-z]+|ref|source|trk|trackingid)$");

    private DedupKeys() {
    }

    /** Adres bez fragmentu, parametrów śledzących i końcowego „/”, z hostem małymi literami. */
    public static String canonicalUrl(String url) {
        if (Text.isBlank(url)) {
            return null;
        }
        try {
            URI u = new URI(url.strip());
            if (u.getScheme() == null || u.getHost() == null) {
                return url.strip();
            }
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            List<String> params = new ArrayList<>();
            if (u.getRawQuery() != null) {
                for (String p : u.getRawQuery().split("&")) {
                    String name = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
                    if (!p.isBlank() && !TRACKING.matcher(name).matches()) {
                        params.add(p);
                    }
                }
            }
            String port = u.getPort() == -1 || u.getPort() == 80 || u.getPort() == 443 ? "" : ":" + u.getPort();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return "https://" + host + port + path + (params.isEmpty() ? "" : "?" + String.join("&", params));
        } catch (Exception e) {
            return url.strip();
        }
    }

    /** Znormalizowany tekst „tytuł | firma” (do porównań i skrótu). */
    public static String normalizedKey(String title, String company) {
        String t = GENDER.matcher(Text.fold(title)).replaceAll(" ");
        String c = LEGAL_FORM.matcher(Text.fold(company)).replaceAll(" ");
        return clean(t) + "|" + clean(c);
    }

    /** Skrót SHA-256 (40 znaków hex) znormalizowanego „tytuł | firma”. */
    public static String contentHash(String title, String company) {
        return FeedParser.sha256(normalizedKey(title, company)).substring(0, 40);
    }

    private static String clean(String s) {
        return s.replaceAll("[^\\p{L}\\p{N}+#]+", " ").replaceAll("\\s+", " ").strip();
    }
}

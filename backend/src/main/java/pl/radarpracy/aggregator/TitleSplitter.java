package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heurystyczne rozbicie tytułu wpisu z kanału na stanowisko, firmę i lokalizację:
 * „Stanowisko — Firma — Miasto”, „Stanowisko | Firma”, „Stanowisko @ Firma (Miasto)”,
 * „Senior Engineer at Acme”, „Stanowisko - Miasto”.
 */
public final class TitleSplitter {

    /** Wynik rozbicia; company i location mogą być null. */
    public record Parts(String title, String company, String location) {
    }

    private static final Pattern SEPARATOR = Pattern.compile("\\s+[—–|·]\\s+|\\s+-\\s+|\\s+\\|\\s*|\\s*\\|\\s+");
    private static final Pattern AT = Pattern.compile("\\s+@\\s*|\\s+at\\s+(?=[\\p{Lu}0-9])");
    private static final Pattern TRAILING_PARENS = Pattern.compile("^(.*\\S)\\s*\\(([^()]{2,60})\\)\\s*$");
    private static final Pattern PLACES = Pattern.compile("(?<![a-z])(" + String.join("|",
            "warszawa", "krakow", "wroclaw", "poznan", "gdansk", "gdynia", "sopot", "trojmiasto", "lodz", "katowice",
            "szczecin", "bydgoszcz", "lublin", "bialystok", "rzeszow", "torun", "kielce", "olsztyn", "opole",
            "gliwice", "zabrze", "bielsko-biala", "czestochowa", "radom", "sosnowiec", "zielona gora", "koszalin",
            "legnica", "kalisz", "plock", "elblag", "tarnow", "chorzow", "rybnik", "tychy", "nowy sacz", "siedlce",
            "pila", "slupsk", "gorzow wielkopolski", "polska", "poland", "cala polska", "berlin", "london", "londyn",
            "praga", "prague", "wieden", "vienna", "amsterdam", "dublin", "zdalnie", "zdalna", "remote", "hybrydowo",
            "dolnoslaskie", "kujawsko-pomorskie", "lubelskie", "lubuskie", "lodzkie", "malopolskie", "mazowieckie",
            "opolskie", "podkarpackie", "podlaskie", "pomorskie", "slaskie", "swietokrzyskie", "warminsko-mazurskie",
            "wielkopolskie", "zachodniopomorskie") + ")(?![a-z])|\\d{2}-\\d{3}");
    private static final Pattern QUALIFIER = Pattern.compile(
            "^(junior|mid|regular|senior|lead|principal|staz|stazysta|intern|b2b|uop|full[- ]?time|part[- ]?time"
                    + "|\\(?[mkx]\\s*/\\s*[mkx](\\s*/\\s*[mkx])?\\)?)(\\W.*)?$|.*\\d.*(zl|pln|eur|usd)\\b.*");

    private TitleSplitter() {
    }

    public static Parts split(String rawTitle) {
        if (Text.isBlank(rawTitle)) {
            return new Parts("", null, null);
        }
        String raw = rawTitle.replaceAll("\\s+", " ").strip();
        List<String> parts = new ArrayList<>();
        for (String p : SEPARATOR.split(raw)) {
            if (!p.isBlank()) {
                parts.add(p.strip());
            }
        }
        String title = parts.isEmpty() ? raw : parts.get(0);
        String company = null;
        String location = null;
        if (parts.size() >= 3) {
            String second = parts.get(1);
            String rest = String.join(", ", parts.subList(2, parts.size()));
            if (isLocation(second) && !isLocation(rest)) {
                location = second;
                company = rest;
            } else {
                company = second;
                location = rest;
            }
        } else if (parts.size() == 2) {
            String second = parts.get(1);
            if (isLocation(second)) {
                location = second;
            } else if (isQualifier(second)) {
                title = raw;
            } else {
                company = second;
            }
        }
        // „Stanowisko @ Firma” w pierwszej części
        Matcher at = AT.matcher(title);
        if (company == null && at.find() && at.start() > 2) {
            String before = title.substring(0, at.start()).strip();
            String after = title.substring(at.end()).strip();
            if (!after.isEmpty()) {
                title = before;
                company = after;
            }
        }
        // „(Miasto)” na końcu firmy albo tytułu
        if (location == null && company != null) {
            Matcher m = TRAILING_PARENS.matcher(company);
            if (m.matches() && isLocation(m.group(2))) {
                company = m.group(1).strip();
                location = m.group(2).strip();
            }
        }
        if (location == null) {
            Matcher m = TRAILING_PARENS.matcher(title);
            if (m.matches() && isLocation(m.group(2))) {
                title = m.group(1).strip();
                location = m.group(2).strip();
            }
        }
        return new Parts(title, blankToNull(company), blankToNull(location));
    }

    /** Czy fragment wygląda na lokalizację (miasto, województwo, „zdalnie”, kod pocztowy). */
    public static boolean isLocation(String s) {
        return !Text.isBlank(s) && s.length() <= 80 && PLACES.matcher(Text.fold(s)).find();
    }

    static boolean isQualifier(String s) {
        return QUALIFIER.matcher(Text.fold(s).strip()).matches();
    }

    private static String blankToNull(String s) {
        return Text.isBlank(s) ? null : s.strip();
    }
}

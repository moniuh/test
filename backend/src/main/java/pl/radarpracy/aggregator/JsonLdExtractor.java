package pl.radarpracy.aggregator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import pl.radarpracy.common.Text;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryRange;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Odczyt ofert z danych strukturalnych schema.org {@code JobPosting} (JSON-LD) na stronach kariery
 * firm (pomysł #44). Obsługuje bloki z pojedynczym obiektem, tablicą, {@code @graph} i {@code ItemList}.
 */
public final class JsonLdExtractor {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS, JsonReadFeature.ALLOW_TRAILING_COMMA,
                    JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .build();
    private static final int MAX_DEPTH = 12;

    /**
     * Wynik odczytu strony.
     *
     * @param items      oferty z bloków JobPosting
     * @param warnings   ostrzeżenia (np. pominięty niepoprawny blok JSON)
     * @param pageTitle  tytuł strony
     * @param pageHidden teksty ukryte stylami w treści strony (poza JSON-LD)
     */
    public record Result(List<FeedItem> items, List<String> warnings, String pageTitle,
                         List<HtmlText.HiddenText> pageHidden) {
    }

    private JsonLdExtractor() {
    }

    public static Result extract(String html, String pageUrl) {
        return extract(Jsoup.parse(Text.nullToEmpty(html), pageUrl == null ? "" : pageUrl), pageUrl);
    }

    public static Result extract(Document doc, String pageUrl) {
        List<String> warnings = new ArrayList<>();
        List<JsonNode> postings = new ArrayList<>();
        int block = 0;
        for (Element script : doc.select("script[type]")) {
            if (!script.attr("type").toLowerCase(Locale.ROOT).contains("ld+json")) {
                continue;
            }
            block++;
            try {
                JsonNode node = MAPPER.readTree(script.data());
                collect(node, postings, 0);
            } catch (JacksonException e) {
                warnings.add("Pominięto niepoprawny blok JSON-LD nr " + block);
            }
        }
        List<FeedItem> items = new ArrayList<>();
        for (JsonNode p : postings) {
            items.add(toItem(p, pageUrl));
        }
        if (block == 0) {
            warnings.add("Strona nie zawiera bloków application/ld+json");
        } else if (items.isEmpty()) {
            warnings.add("Bloki JSON-LD nie zawierają ofert typu JobPosting");
        }
        return new Result(items, warnings, doc.title(), HtmlText.hiddenTexts(doc.body()));
    }

    static void collect(JsonNode node, List<JsonNode> out, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode v : node.values()) {
                collect(v, out, depth + 1);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (isType(node, "JobPosting")) {
            out.add(node);
            return;
        }
        collect(node.get("@graph"), out, depth + 1);
        JsonNode list = node.get("itemListElement");
        if (list != null) {
            for (JsonNode el : list.isArray() ? list.values() : List.of(list)) {
                collect(el.has("item") ? el.get("item") : el, out, depth + 1);
            }
        }
    }

    static boolean isType(JsonNode node, String type) {
        JsonNode t = node.get("@type");
        if (t == null) {
            return false;
        }
        for (JsonNode v : t.isArray() ? t.values() : List.of(t)) {
            if (v.isString()) {
                String s = v.stringValue();
                if (s.equalsIgnoreCase(type) || s.endsWith("/" + type) || s.endsWith(":" + type)) {
                    return true;
                }
            }
        }
        return false;
    }

    static FeedItem toItem(JsonNode p, String pageUrl) {
        String title = firstNonBlank(text(p, "title"), text(p, "name"), "(bez tytułu)");
        String company = organization(p.get("hiringOrganization"));
        boolean remote = "TELECOMMUTE".equalsIgnoreCase(Text.nullToEmpty(text(p, "jobLocationType")));
        String location = location(p.get("jobLocation"));
        if (location == null && remote) {
            location = "zdalnie";
        }
        String rawDescription = Text.nullToEmpty(text(p, "description"));
        if (!HtmlText.looksLikeHtml(rawDescription) && rawDescription.contains("&lt;")) {
            rawDescription = Parser.unescapeEntities(rawDescription, false);
        }
        String description = HtmlText.toText(rawDescription);
        String url = FeedParser.absolute(text(p, "url"), pageUrl);
        String identifier = identifier(p.get("identifier"));
        String externalId = firstNonBlank(identifier, url);
        if (externalId == null) {
            externalId = "sha:" + FeedParser.sha256(title + "\n" + Text.nullToEmpty(company)).substring(0, 32);
        }
        List<String> employment = new ArrayList<>();
        JsonNode et = p.get("employmentType");
        if (et != null) {
            for (JsonNode v : et.isArray() ? et.values() : List.of(et)) {
                if (v.isString()) {
                    for (String part : v.stringValue().split("[,;]")) {
                        if (!part.isBlank()) {
                            employment.add(part.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
                        }
                    }
                }
            }
        }
        List<String> categories = new ArrayList<>();
        String occupational = text(p, "occupationalCategory");
        if (occupational != null) {
            categories.add(occupational);
        }
        return new FeedItem(Text.truncate(externalId, 1000), Text.truncate(title.strip(), 250),
                Text.truncate(company, 250), Text.truncate(location, 250), url != null ? url : pageUrlOrNull(pageUrl),
                description, Dates.parse(text(p, "datePosted")), Dates.parse(text(p, "validThrough")), null,
                categories, employment, salary(p.get("baseSalary")), remote ? Boolean.TRUE : null,
                HtmlText.hiddenTexts(rawDescription));
    }

    private static String pageUrlOrNull(String pageUrl) {
        return FeedParser.absolute(pageUrl, null);
    }

    static String organization(JsonNode org) {
        if (org == null) {
            return null;
        }
        if (org.isString()) {
            return blankToNull(org.stringValue());
        }
        if (org.isArray()) {
            for (JsonNode v : org.values()) {
                String name = organization(v);
                if (name != null) {
                    return name;
                }
            }
            return null;
        }
        return text(org, "name");
    }

    static String location(JsonNode loc) {
        if (loc == null) {
            return null;
        }
        Set<String> places = new LinkedHashSet<>();
        for (JsonNode l : loc.isArray() ? loc.values() : List.of(loc)) {
            if (l.isString()) {
                addIfPresent(places, l.stringValue());
                continue;
            }
            JsonNode address = l.get("address");
            if (address != null && address.isString()) {
                addIfPresent(places, address.stringValue());
            } else if (address != null && address.isObject()) {
                addIfPresent(places, firstNonBlank(text(address, "addressLocality"), text(address, "addressRegion"),
                        text(address, "addressCountry")));
            } else {
                addIfPresent(places, text(l, "name"));
            }
        }
        return places.isEmpty() ? null : String.join(", ", places);
    }

    static String identifier(JsonNode id) {
        if (id == null) {
            return null;
        }
        if (id.isString() || id.isNumber()) {
            return blankToNull(id.asString());
        }
        if (id.isObject()) {
            String value = text(id, "value");
            String name = text(id, "name");
            if (value == null) {
                return null;
            }
            return name == null ? value : name + ":" + value;
        }
        return null;
    }

    /** baseSalary (MonetaryAmount) → widełki. Zwraca null, gdy brak sensownych kwot. */
    static SalaryRange salary(JsonNode base) {
        if (base == null || !base.isObject()) {
            return null;
        }
        String currency = Text.nullToEmpty(text(base, "currency")).toUpperCase(Locale.ROOT);
        JsonNode value = base.get("value");
        Double min = null;
        Double max = null;
        String unit = text(base, "unitText");
        if (value != null && value.isObject()) {
            min = number(value.get("minValue"));
            max = number(value.get("maxValue"));
            Double single = number(value.get("value"));
            if (min == null && max == null && single != null) {
                min = single;
                max = single;
            }
            unit = firstNonBlank(text(value, "unitText"), unit);
        } else if (value != null) {
            Double single = number(value);
            min = single;
            max = single;
        }
        if (min == null && max == null) {
            min = number(base.get("minValue"));
            max = number(base.get("maxValue"));
        }
        double factor = 1.0;
        SalaryPeriod period = null;
        String u = Text.nullToEmpty(unit).toUpperCase(Locale.ROOT);
        switch (u) {
            case "HOUR", "HOURLY" -> period = SalaryPeriod.HOUR;
            case "DAY", "DAILY" -> period = SalaryPeriod.DAY;
            case "WEEK", "WEEKLY" -> {
                period = SalaryPeriod.MONTH;
                factor = 52.0 / 12.0;
            }
            case "MONTH", "MONTHLY" -> period = SalaryPeriod.MONTH;
            case "YEAR", "YEARLY", "ANNUAL" -> period = SalaryPeriod.YEAR;
            default -> {
            }
        }
        Integer a = toAmount(min, factor);
        Integer b = toAmount(max, factor);
        if (a == null && b == null) {
            return null;
        }
        if (a != null && b != null && b < a) {
            Integer t = a;
            a = b;
            b = t;
        }
        if (period == null) {
            int ref = b != null ? b : a;
            period = ref < 500 ? SalaryPeriod.HOUR : ref >= 100_000 ? SalaryPeriod.YEAR : SalaryPeriod.MONTH;
        }
        String cur = currency.isBlank() ? "PLN" : currency;
        String described = "baseSalary (JSON-LD): " + (a == null ? "" : a) + (b != null && !b.equals(a) ? "–" + b : "")
                + " " + cur + " / " + period;
        return new SalaryRange(a, b, cur, period, null, null, described);
    }

    private static Integer toAmount(Double v, double factor) {
        if (v == null || v <= 0) {
            return null;
        }
        double x = v * factor;
        return x > 10_000_000 ? null : (int) Math.round(x);
    }

    static Double number(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isString()) {
            String s = n.stringValue().replaceAll("[\\s\\u00a0]", "").replace(',', '.');
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Wartość tekstowa pola (string, liczba, {@code @value} albo pierwszy element tablicy). */
    static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode v = node.get(field);
        return scalar(v);
    }

    private static String scalar(JsonNode v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            return blankToNull(v.stringValue());
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asString();
        }
        if (v.isArray()) {
            for (JsonNode x : v.values()) {
                String s = scalar(x);
                if (s != null) {
                    return s;
                }
            }
            return null;
        }
        if (v.isObject() && v.has("@value")) {
            return scalar(v.get("@value"));
        }
        return null;
    }

    private static void addIfPresent(Set<String> set, String v) {
        if (!Text.isBlank(v)) {
            set.add(v.strip());
        }
    }

    private static String blankToNull(String s) {
        return Text.isBlank(s) ? null : s.strip();
    }

    private static String firstNonBlank(String... values) {
        return FeedParser.firstNonBlank(values);
    }
}

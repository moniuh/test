package pl.radarpracy.aggregator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Konektory źródeł: RSS/Atom (pomysł #9), strona kariery z JSON-LD (pomysł #44) i pliki demonstracyjne
 * z {@code classpath:aggregator/} (bez sieci). Zwraca pozycje gotowe do analizy.
 */
@Component
public class SourceConnector {

    static final String DEMO_DIR = "aggregator/";
    private static final Pattern DEMO_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,100}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(date|rfc)([+-]\\d{1,3})?}}");
    private static final String FEED_ACCEPT =
            "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5";
    private static final String HTML_ACCEPT = "text/html, application/xhtml+xml;q=0.9, */*;q=0.5";

    /**
     * Wynik pobrania źródła.
     *
     * @param items    pozycje (najwyżej {@code maxItemsPerFetch})
     * @param format   rozpoznany format: RSS, ATOM, JSONLD
     * @param title    tytuł kanału / strony
     * @param warnings ostrzeżenia do pokazania w historii
     */
    public record FetchOutcome(List<FeedItem> items, String format, String title, List<String> warnings) {
    }

    private final HttpFetcher fetcher;
    private final AggregatorProperties props;

    public SourceConnector(HttpFetcher fetcher, AggregatorProperties props) {
        this.fetcher = fetcher;
        this.props = props;
    }

    public FetchOutcome fetch(SourceType type, String url) {
        if (type == null) {
            throw new SourceException("Wybierz typ źródła");
        }
        FetchOutcome outcome = switch (type) {
            case DEMO -> demo(url);
            case RSS, ATOM -> feed(type, url);
            case JSONLD_PAGE -> careersPage(url);
        };
        return limit(outcome);
    }

    private FetchOutcome feed(SourceType declared, String url) {
        HttpFetcher.FetchResult r = fetcher.fetch(url, FEED_ACCEPT);
        if (!r.looksLikeXml()) {
            throw new SourceException("Adres nie zwrócił kanału RSS/Atom (Content-Type: "
                    + (r.contentType() == null ? "brak" : r.contentType())
                    + "). Jeśli to strona kariery firmy, wybierz typ „Strona kariery (JSON-LD)”.");
        }
        FeedParser.Feed feed = FeedParser.parse(r.body(), r.url());
        List<String> warnings = new ArrayList<>();
        if (!declared.name().equals(feed.format().name())) {
            warnings.add("Źródło oznaczone jako " + declared + " zwróciło kanał " + feed.format()
                    + " — przetworzono automatycznie");
        }
        return new FetchOutcome(feed.items(), feed.format().name(), feed.title(), warnings);
    }

    private FetchOutcome careersPage(String url) {
        HttpFetcher.FetchResult r = fetcher.fetch(url, HTML_ACCEPT);
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(r.body()), r.charset(), r.url());
        } catch (IOException e) {
            throw new SourceException("Nie udało się odczytać strony: " + e.getMessage());
        }
        return fromPage(JsonLdExtractor.extract(doc, r.url()));
    }

    private FetchOutcome demo(String name) {
        String content = new String(loadDemo(name), StandardCharsets.UTF_8);
        content = applyPlaceholders(content, LocalDate.now());
        String head = content.stripLeading();
        if (head.startsWith("<?xml") || head.startsWith("<rss") || head.startsWith("<feed")) {
            FeedParser.Feed feed = FeedParser.parse(content, null);
            return new FetchOutcome(feed.items(), feed.format().name(), feed.title(), List.of());
        }
        return fromPage(JsonLdExtractor.extract(content, null));
    }

    /** Strona kariery: pozycje JSON-LD + ukryte na stronie polecenia dla AI dołączone do każdej pozycji. */
    private static FetchOutcome fromPage(JsonLdExtractor.Result result) {
        List<HtmlText.HiddenText> suspicious = result.pageHidden().stream()
                .filter(h -> InjectionDetector.containsInstruction(h.text()))
                .toList();
        List<FeedItem> items = new ArrayList<>();
        for (FeedItem item : result.items()) {
            items.add(suspicious.isEmpty() ? item : item.withHiddenTexts(suspicious));
        }
        List<String> warnings = new ArrayList<>(result.warnings());
        if (!suspicious.isEmpty()) {
            warnings.add("Na stronie znaleziono ukryty tekst z poleceniami dla AI (" + suspicious.size() + ")");
        }
        return new FetchOutcome(items, "JSONLD", result.pageTitle(), warnings);
    }

    private FetchOutcome limit(FetchOutcome o) {
        int max = props.maxItemsPerFetch();
        if (o.items().size() <= max) {
            return o;
        }
        List<String> warnings = new ArrayList<>(o.warnings());
        warnings.add("Przetworzono pierwsze " + max + " z " + o.items().size() + " pozycji (limit max-items-per-fetch)");
        return new FetchOutcome(List.copyOf(o.items().subList(0, max)), o.format(), o.title(), warnings);
    }

    /** Nazwa pliku demonstracyjnego bez katalogów (ochrona przed „../”). */
    static String demoFileName(String raw) {
        String name = Text.nullToEmpty(raw).strip();
        if (name.startsWith("classpath:")) {
            name = name.substring("classpath:".length());
        }
        if (name.startsWith(DEMO_DIR)) {
            name = name.substring(DEMO_DIR.length());
        }
        if (!DEMO_NAME.matcher(name).matches() || name.contains("..")) {
            throw new SourceException("Dla typu DEMO podaj nazwę pliku z katalogu aggregator/, np. demo-feed.xml");
        }
        return name;
    }

    static byte[] loadDemo(String raw) {
        String name = demoFileName(raw);
        ClassPathResource res = new ClassPathResource(DEMO_DIR + name);
        if (!res.exists()) {
            throw new SourceException("Nie znaleziono pliku demonstracyjnego " + DEMO_DIR + name);
        }
        try (InputStream in = res.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new SourceException("Nie udało się odczytać pliku " + name + ": " + e.getMessage());
        }
    }

    /**
     * Daty w plikach demonstracyjnych liczone względem dnia uruchomienia: {@code {{date-3}}} → ISO,
     * {@code {{rfc-3}}} → RFC 1123 (pubDate w RSS).
     */
    static String applyPlaceholders(String content, LocalDate today) {
        Matcher m = PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int offset = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            LocalDate d = today.plusDays(offset);
            String value = m.group(1).equals("date") ? d.toString()
                    : DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
                    .format(d.atTime(9, 0).atZone(ZoneId.systemDefault()));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

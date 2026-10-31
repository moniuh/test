package pl.radarpracy.offer.analysis;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Import oferty z adresu URL (pomysł #14): pobranie strony na żądanie użytkownika, odczyt danych
 * strukturalnych schema.org {@code JobPosting} (JSON-LD), a w razie ich braku — głównej treści
 * strony; następnie parser struktury oferty (#13).
 */
@Service
public class OfferImporter {

    /** Minimalna długość treści, poniżej której uznajemy, że strony nie udało się odczytać. */
    static final int MIN_TEXT = 120;

    private final OfferTextParser parser;
    private final PageFetcher fetcher;
    private final UrlGuard.Resolver resolver;

    @Autowired
    public OfferImporter(OfferTextParser parser, PageFetcher fetcher) {
        this(parser, fetcher, UrlGuard.SYSTEM_DNS);
    }

    OfferImporter(OfferTextParser parser, PageFetcher fetcher, UrlGuard.Resolver resolver) {
        this.parser = parser;
        this.fetcher = fetcher;
        this.resolver = resolver;
    }

    /** Pobiera i analizuje stronę ogłoszenia. */
    public ParsedOffer importUrl(String url) {
        URI uri = UrlGuard.validate(url, resolver);
        PageFetcher.FetchedPage page = fetcher.fetch(uri);
        return process(page.document(), url.strip());
    }

    /** Analiza już pobranego HTML (bez sieci — używane w testach). */
    public ParsedOffer process(String html, String url) {
        return process(Jsoup.parse(html, url == null ? "" : url), url);
    }

    /** Analiza pobranego dokumentu: najpierw JSON-LD {@code JobPosting}, potem główna treść strony. */
    public ParsedOffer process(Document doc, String url) {
        List<String> warnings = new ArrayList<>();
        Optional<JobPostingData> ld = JobPostingExtractor.extract(doc);
        String pageText = HtmlText.mainContent(doc);

        String description;
        String origin;
        if (ld.isPresent() && ld.get().description() != null && ld.get().description().length() >= MIN_TEXT) {
            JobPostingData d = ld.get();
            // nagłówek z danych strukturalnych + opis — parser rozpozna z niego resztę pól
            StringBuilder sb = new StringBuilder();
            if (d.title() != null) {
                sb.append(d.title());
                if (d.company() != null) {
                    sb.append(" — ").append(d.company());
                }
                sb.append('\n');
            }
            if (d.location() != null) {
                sb.append("Lokalizacja: ").append(d.location()).append('\n');
            }
            sb.append('\n').append(d.description());
            description = sb.toString();
            origin = "json-ld";
        } else {
            description = pageText;
            origin = ld.isPresent() ? "json-ld" : "html";
        }
        if (Text.isBlank(description) || description.strip().length() < MIN_TEXT) {
            throw new OfferImportException("empty",
                    "Na stronie nie znaleziono treści ogłoszenia (może być wczytywana skryptem lub wymagać logowania)");
        }

        OfferTextParser.Builder b = parser.analyze(description, url);
        ld.ifPresent(d -> overlay(b, d));
        if (ld.isEmpty()) {
            warnings.add("Strona nie publikuje danych strukturalnych JobPosting — pola rozpoznano z treści strony, sprawdź je.");
        }
        if (b.description != null && b.description.length() >= OfferTextParser.MAX_TEXT) {
            warnings.add("Treść strony była bardzo długa i została skrócona.");
        }
        return b.build(origin, warnings);
    }

    /** Dane strukturalne mają pierwszeństwo przed heurystyką tekstową (wysoka pewność). */
    private static void overlay(OfferTextParser.Builder b, JobPostingData d) {
        b.extraSources.add(d.raw());
        String method = "dane strukturalne JobPosting (JSON-LD)";
        if (d.title() != null) {
            b.title = d.title();
            b.field("title", d.title(), 0.95, d.title(), method);
        }
        if (d.company() != null) {
            b.company = d.company();
            b.field("company", d.company(), 0.95, d.company(), method);
        }
        if (d.location() != null) {
            b.location = d.location();
            b.field("location", d.location(), 0.9, d.location(), method);
        }
        if (d.workMode() != null && b.workMode == null) {
            b.workMode = d.workMode();
            b.field("workMode", OfferTextParser.workModeLabel(d.workMode()), 0.9, null, method + " — jobLocationType");
        }
        if (d.salaryMin() != null || d.salaryMax() != null) {
            b.salaryMin = d.salaryMin();
            b.salaryMax = d.salaryMax();
            b.salaryCurrency = d.salaryCurrency() == null ? "PLN" : d.salaryCurrency();
            b.salaryPeriod = d.salaryPeriod();
            String value = (d.salaryMin() == null ? "" : d.salaryMin()) + (d.salaryMax() == null ? "" : "–" + d.salaryMax())
                    + " " + b.salaryCurrency;
            // brutto/netto nie wynika z JSON-LD — zostawiamy wartość z treści (jeśli była)
            b.field("salary", value.strip(), b.salaryGross == null ? 0.8 : 0.9, null, method + " — baseSalary");
        }
        if (!d.contractTypes().isEmpty()) {
            Set<ContractType> merged = new LinkedHashSet<>(d.contractTypes());
            merged.addAll(b.contractTypes);
            b.contractTypes = new ArrayList<>(merged);
            b.field("contractTypes", String.join(", ", merged.stream().map(OfferTextParser::contractLabel).toList()),
                    0.9, null, method + " — employmentType");
        }
        if (d.publishedAt() != null) {
            b.publishedAt = d.publishedAt();
        }
        if (d.expiresAt() != null) {
            b.expiresAt = d.expiresAt();
            b.field("expiresAt", d.expiresAt().toString(), 0.95, null, method + " — validThrough");
        }
    }
}

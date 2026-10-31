package pl.radarpracy.offer.analysis;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.stereotype.Component;
import pl.radarpracy.common.BadRequestException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Pobieranie strony przez Jsoup: tylko na żądanie użytkownika, limit czasu 10 s, limit rozmiaru 3 MB,
 * jawny User-Agent, przekierowania obsługiwane ręcznie (maks. 5) z ponowną kontrolą SSRF.
 */
@Component
public class JsoupPageFetcher implements PageFetcher {

    static final int TIMEOUT_MS = 10_000;
    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;
    static final String USER_AGENT =
            "Mozilla/5.0 (compatible; RadarPracy/0.2; import ogloszenia na zadanie uzytkownika)";

    private final UrlGuard.Resolver resolver;

    public JsoupPageFetcher() {
        this(UrlGuard.SYSTEM_DNS);
    }

    JsoupPageFetcher(UrlGuard.Resolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public FetchedPage fetch(URI start) {
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Connection.Response res;
            try {
                res = Jsoup.connect(uri.toString())
                        .userAgent(USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
                        .timeout(TIMEOUT_MS)
                        .maxBodySize(MAX_BYTES)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
            } catch (SocketTimeoutException e) {
                throw new OfferImportException("timeout", "Strona nie odpowiedziała w ciągu 10 sekund");
            } catch (UnknownHostException e) {
                throw new OfferImportException("unreachable", "Nie znaleziono serwera — sprawdź adres");
            } catch (UnsupportedMimeTypeException e) {
                throw new OfferImportException("not_html", "Adres nie wskazuje na stronę HTML (" + e.getMimeType() + ")");
            } catch (HttpStatusException e) {
                throw httpError(e.getStatusCode());
            } catch (IOException e) {
                throw new OfferImportException("unreachable", "Nie udało się połączyć ze stroną ogłoszenia");
            }
            int status = res.statusCode();
            if (status >= 300 && status < 400 && res.header("Location") != null) {
                URI next;
                try {
                    next = uri.resolve(res.header("Location").strip());
                } catch (IllegalArgumentException e) {
                    throw new OfferImportException("http_error", "Strona przekierowuje na niepoprawny adres");
                }
                try {
                    uri = UrlGuard.validate(next.toString(), resolver);
                } catch (BadRequestException e) {
                    throw new OfferImportException("blocked", "Strona przekierowuje na niedozwolony adres: " + e.getMessage());
                }
                continue;
            }
            if (status >= 400) {
                throw httpError(status);
            }
            String type = res.contentType();
            if (type != null && !type.contains("html") && !type.contains("xml")) {
                throw new OfferImportException("not_html", "Adres nie wskazuje na stronę HTML (" + type + ")");
            }
            try {
                return new FetchedPage(res.parse(), uri.toString());
            } catch (IOException e) {
                throw new OfferImportException("unreachable", "Nie udało się odczytać strony ogłoszenia");
            }
        }
        throw new OfferImportException("http_error", "Za dużo przekierowań");
    }

    private static OfferImportException httpError(int status) {
        return switch (status) {
            case 401, 403, 429, 451 -> new OfferImportException("blocked",
                    "Portal zablokował automatyczne pobieranie ogłoszenia (HTTP " + status + ")");
            case 404, 410 -> new OfferImportException("not_found",
                    "Ogłoszenie nie istnieje lub wygasło (HTTP " + status + ")");
            default -> new OfferImportException("http_error", "Serwer zwrócił błąd HTTP " + status);
        };
    }
}

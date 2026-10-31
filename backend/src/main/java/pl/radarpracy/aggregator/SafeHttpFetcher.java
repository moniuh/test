package pl.radarpracy.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pobieranie z sieci z „etykietą” (pomysł #44): tylko http/https, ochrona SSRF na każdym przekierowaniu,
 * poszanowanie robots.txt (pamiętane przez godzinę), odstęp między zapytaniami do tej samej domeny,
 * identyfikujący User-Agent, limit czasu i limit rozmiaru odpowiedzi.
 */
@Component
public class SafeHttpFetcher implements HttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpFetcher.class);
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Duration ROBOTS_TTL = Duration.ofHours(1);
    private static final Duration ROBOTS_ERROR_TTL = Duration.ofMinutes(10);

    private record CachedRobots(RobotsTxt robots, long expiresAtMillis) {
    }

    private final AggregatorProperties props;
    private final UrlGuard guard;
    private final HttpClient client;
    private final HostThrottle throttle = new HostThrottle();
    private final Map<String, CachedRobots> robotsCache = new ConcurrentHashMap<>();

    @Autowired
    public SafeHttpFetcher(AggregatorProperties props) {
        this(props, new UrlGuard());
    }

    SafeHttpFetcher(AggregatorProperties props, UrlGuard guard) {
        this.props = props;
        this.guard = guard;
        HttpClient.Builder b = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(props.requestTimeoutSeconds()));
        ProxySelector proxy = ProxySelector.getDefault();
        if (proxy != null) {
            b.proxy(proxy);
        }
        this.client = b.build();
    }

    @Override
    public FetchResult fetch(String url, String accept) {
        URI uri = guard.check(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            ensureAllowedByRobots(uri);
            HttpResponse<InputStream> response = send(uri, accept);
            int status = response.statusCode();
            if (REDIRECTS.contains(status)) {
                close(response);
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    throw new SourceException("Przekierowanie bez nagłówka Location (" + uri + ")");
                }
                uri = guard.check(uri.resolve(location.strip()).toString());
                continue;
            }
            if (status >= 400) {
                close(response);
                throw new SourceException("Serwer zwrócił błąd HTTP " + status + " dla " + uri);
            }
            byte[] body = readLimited(response);
            return new FetchResult(uri.toString(), status, response.headers().firstValue("Content-Type").orElse(null),
                    body);
        }
        throw new SourceException("Zbyt wiele przekierowań (ponad " + MAX_REDIRECTS + ")");
    }

    /** Zgodność z robots.txt dla adresu; rzuca wyjątek, gdy pobranie jest zabronione. */
    void ensureAllowedByRobots(URI uri) {
        RobotsTxt robots = robotsFor(uri);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        if (!robots.isAllowed(props.userAgent(), path)) {
            throw new SourceException("Plik robots.txt serwisu " + uri.getHost() + " nie pozwala automatycznie pobierać "
                    + "ścieżki " + path + " — agregator respektuje to ustawienie", true);
        }
    }

    private RobotsTxt robotsFor(URI uri) {
        String origin = origin(uri);
        CachedRobots cached = robotsCache.get(origin);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.robots();
        }
        RobotsTxt robots;
        long ttl = ROBOTS_TTL.toMillis();
        URI robotsUri = URI.create(origin + "/robots.txt");
        try {
            URI current = robotsUri;
            HttpResponse<InputStream> response = null;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                response = send(current, "text/plain");
                if (!REDIRECTS.contains(response.statusCode())) {
                    break;
                }
                close(response);
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    break;
                }
                current = guard.check(current.resolve(location.strip()).toString());
            }
            int status = response == null ? 0 : response.statusCode();
            if (status >= 200 && status < 300) {
                robots = RobotsTxt.parse(new String(readLimited(response), StandardCharsets.UTF_8));
            } else if (status >= 400 && status < 500) {
                close(response);
                robots = RobotsTxt.allowAll();
            } else {
                close(response);
                robots = RobotsTxt.disallowAll();
                ttl = ROBOTS_ERROR_TTL.toMillis();
                log.info("robots.txt {} niedostępny (HTTP {}) — wstrzymuję pobieranie z tej domeny", origin, status);
            }
        } catch (SourceException e) {
            throw new SourceException("Nie udało się sprawdzić robots.txt (" + origin + "): " + e.getMessage());
        }
        robotsCache.put(origin, new CachedRobots(robots, now + ttl));
        return robots;
    }

    private HttpResponse<InputStream> send(URI uri, String accept) {
        throttle.acquire(uri.getHost(), props.perHostDelayMs());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("User-Agent", props.userAgent())
                .header("Accept", accept == null ? "*/*" : accept)
                .header("Accept-Language", "pl,en;q=0.8")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new SourceException("Przekroczono limit czasu (" + props.requestTimeoutSeconds() + " s) dla " + uri);
        } catch (IOException e) {
            throw new SourceException("Nie udało się połączyć z " + uri.getHost() + ": "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceException("Pobieranie przerwane");
        }
    }

    private byte[] readLimited(HttpResponse<InputStream> response) {
        long limit = props.maxResponseBytes();
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > limit) {
            close(response);
            throw new SourceException("Odpowiedź jest za duża (" + declared / 1024 + " KB, limit " + limit / 1024 + " KB)");
        }
        try (InputStream in = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > limit) {
                    throw new SourceException("Odpowiedź przekracza limit rozmiaru (" + limit / 1024 + " KB)");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new SourceException("Błąd odczytu odpowiedzi: " + e.getMessage());
        }
    }

    private static void close(HttpResponse<InputStream> response) {
        if (response == null) {
            return;
        }
        try {
            response.body().close();
        } catch (IOException ignored) {
            // nic do zrobienia
        }
    }

    static String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);
        return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }
}

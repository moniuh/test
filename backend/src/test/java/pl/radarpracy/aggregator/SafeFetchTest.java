package pl.radarpracy.aggregator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SSRF, robots.txt, przekierowania, limit rozmiaru i limit zapytań na domenę. */
class SafeFetchTest {

    private HttpServer server;
    private String base;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/feed", "http://localhost:8080/x", "http://10.1.2.3/", "http://172.20.0.1/",
            "http://192.168.0.10/rss", "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://[fd00::1]/",
            "http://100.64.1.1/", "http://0.0.0.0/", "http://[::ffff:10.0.0.1]/", "http://intranet.local/",
            "http://metadata.google.internal/"})
    void privateAndLocalAddressesAreRejected(String url) {
        UrlGuard guard = new UrlGuard();
        assertThatThrownBy(() -> guard.check(url)).isInstanceOf(SourceException.class).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> guard.checkStatic(url)).isInstanceOf(SourceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/feed", "gopher://example.com/", "jobs.example.com/rss",
            "http://user:secret@example.com/feed", ""})
    void onlyPlainHttpUrlsAreAccepted(String url) {
        assertThatThrownBy(() -> UrlGuard.parseHttpUrl(url)).isInstanceOf(SourceException.class);
    }

    @Test
    void hostnamesAreCheckedAfterDnsResolution() throws UnknownHostException {
        Map<String, InetAddress[]> dns = Map.of(
                "intranet.example.com", new InetAddress[]{InetAddress.getByName("10.0.0.7")},
                "mixed.example.com", new InetAddress[]{InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")},
                "jobs.example.com", new InetAddress[]{InetAddress.getByName("93.184.216.34")});
        UrlGuard guard = new UrlGuard(host -> {
            InetAddress[] a = dns.get(host);
            if (a == null) {
                throw new UnknownHostException(host);
            }
            return a;
        }, false);
        assertThatThrownBy(() -> guard.check("https://intranet.example.com/rss")).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> guard.check("https://mixed.example.com/rss")).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> guard.check("https://nieznany.example.com/rss")).hasMessageContaining("DNS");
        assertThat(guard.check("https://jobs.example.com/rss").getHost()).isEqualTo("jobs.example.com");
        // przy zapisie źródła nie odpytujemy DNS
        assertThat(guard.checkStatic("https://nieznany.example.com/rss")).isNotNull();
    }

    @Test
    void fetcherRefusesPrivateAddressBeforeConnecting() {
        SafeHttpFetcher fetcher = new SafeHttpFetcher(AggregatorProperties.defaults());
        assertThatThrownBy(() -> fetcher.fetch("http://127.0.0.1:9/feed.xml", "*/*"))
                .isInstanceOf(SourceException.class).hasMessageContaining("SSRF");
    }

    // ------------------------------------------------------------------ lokalny serwer (loopback dozwolony tylko w teście)

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            switch (path) {
                case "/robots.txt" -> respond(exchange, 200, "text/plain",
                        "User-agent: *\nDisallow: /prywatne\n");
                case "/feed.xml" -> respond(exchange, 200, "application/rss+xml; charset=utf-8",
                        "<rss version=\"2.0\"><channel><title>T</title><item><title>Java Developer — Acme — Kraków</title>"
                                + "<link>https://acme.example/1</link><guid>1</guid></item></channel></rss>");
                case "/przekierowanie" -> {
                    exchange.getResponseHeaders().add("Location", "/feed.xml");
                    respond(exchange, 302, "text/plain", "");
                }
                case "/do-sieci-prywatnej" -> {
                    exchange.getResponseHeaders().add("Location", "http://10.0.0.1/feed.xml");
                    respond(exchange, 301, "text/plain", "");
                }
                case "/duzy" -> respond(exchange, 200, "text/html", "x".repeat(4096));
                default -> respond(exchange, 404, "text/plain", "brak");
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
        ex.close();
    }

    private SafeHttpFetcher testFetcher(long maxBytes) {
        AggregatorProperties props = new AggregatorProperties(false, 60, "RadarPracyTest/1.0", 5, 0, 200, 45, maxBytes);
        return new SafeHttpFetcher(props, new UrlGuard(InetAddress::getAllByName, true));
    }

    @Test
    void fetchesFeedWithUserAgentAfterCheckingRobotsAndFollowsRedirects() {
        SafeHttpFetcher fetcher = testFetcher(0);
        HttpFetcher.FetchResult r = fetcher.fetch(base + "/przekierowanie", "application/rss+xml");
        assertThat(r.url()).endsWith("/feed.xml");
        assertThat(r.looksLikeXml()).isTrue();
        assertThat(FeedParser.parse(r.body(), r.url()).items()).hasSize(1);
        assertThat(paths.get(0)).isEqualTo("/robots.txt");
        assertThat(userAgents).allMatch("RadarPracyTest/1.0"::equals);
        // robots.txt pobrany raz (pamięć podręczna)
        fetcher.fetch(base + "/feed.xml", "*/*");
        assertThat(paths.stream().filter("/robots.txt"::equals).count()).isEqualTo(1);
    }

    @Test
    void robotsDisallowBlocksPath() {
        SafeHttpFetcher fetcher = testFetcher(0);
        assertThatThrownBy(() -> fetcher.fetch(base + "/prywatne/oferty", "*/*"))
                .isInstanceOfSatisfying(SourceException.class, e -> {
                    assertThat(e.blockedByRobots()).isTrue();
                    assertThat(e.getMessage()).contains("robots.txt");
                });
        assertThat(paths).doesNotContain("/prywatne/oferty");
    }

    @Test
    void redirectToPrivateNetworkAndOversizedResponseAreRejected() {
        SafeHttpFetcher fetcher = testFetcher(1024);
        assertThatThrownBy(() -> fetcher.fetch(base + "/do-sieci-prywatnej", "*/*")).hasMessageContaining("SSRF");
        assertThatThrownBy(() -> fetcher.fetch(base + "/duzy", "*/*")).hasMessageContaining("limit");
        assertThatThrownBy(() -> fetcher.fetch(base + "/nie-ma", "*/*")).hasMessageContaining("HTTP 404");
    }

    @Test
    void perHostDelayIsRespected() {
        HostThrottle throttle = new HostThrottle();
        long start = System.nanoTime();
        throttle.acquire("a.example", 120);
        throttle.acquire("a.example", 120);
        throttle.acquire("b.example", 120);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(110).isLessThan(1000);
    }
}

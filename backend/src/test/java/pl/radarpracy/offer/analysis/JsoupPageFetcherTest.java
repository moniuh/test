package pl.radarpracy.offer.analysis;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pobieranie strony (#14) na lokalnym serwerze HTTP w procesie testu — bez sieci zewnętrznej.
 * Pierwsze połączenie idzie na 127.0.0.1 (kontrolę SSRF startowego adresu robi {@link OfferImporter}),
 * a każde przekierowanie jest ponownie sprawdzane przez {@link UrlGuard}.
 */
class JsoupPageFetcherTest {

    private HttpServer server;
    private String base;
    private final JsoupPageFetcher fetcher = new JsoupPageFetcher(
            host -> new InetAddress[]{InetAddress.getByName(host.equals("public.example") ? "93.184.215.14" : host)});

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> respond(ex, 200, "text/html; charset=utf-8",
                "<html><body><h1>Java Developer</h1><p>Treść ogłoszenia</p></body></html>"));
        server.createContext("/blocked", ex -> respond(ex, 403, "text/html", "<p>Access denied</p>"));
        server.createContext("/gone", ex -> respond(ex, 404, "text/html", "<p>Not found</p>"));
        server.createContext("/pdf", ex -> respond(ex, 200, "application/pdf", "%PDF-1.4"));
        server.createContext("/to-metadata", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
            respond(ex, 302, "text/plain", "");
        });
        server.createContext("/to-ftp", ex -> {
            ex.getResponseHeaders().add("Location", "ftp://public.example/file");
            respond(ex, 301, "text/plain", "");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
        ex.close();
    }

    @Test
    void fetchesHtml() {
        PageFetcher.FetchedPage page = fetcher.fetch(URI.create(base + "/ok"));
        assertThat(page.document().selectFirst("h1").text()).isEqualTo("Java Developer");
        assertThat(page.finalUrl()).endsWith("/ok");
    }

    @Test
    void blockedAndMissingPagesHaveReasons() {
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/blocked")))
                .isInstanceOf(OfferImportException.class)
                .hasMessageContaining("HTTP 403")
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("blocked"));
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/gone")))
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("not_found"));
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/pdf")))
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("not_html"));
    }

    @Test
    void redirectToPrivateAddressOrOtherSchemeIsBlocked() {
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/to-metadata")))
                .isInstanceOf(OfferImportException.class)
                .hasMessageContaining("niedozwolony adres")
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("blocked"));
        assertThatThrownBy(() -> fetcher.fetch(URI.create(base + "/to-ftp")))
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("blocked"));
    }
}

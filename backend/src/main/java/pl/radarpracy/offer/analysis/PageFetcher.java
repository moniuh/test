package pl.radarpracy.offer.analysis;

import org.jsoup.nodes.Document;

import java.net.URI;

/** Pobranie strony z ogłoszeniem — osobny interfejs, żeby testy działały bez sieci. */
public interface PageFetcher {

    /**
     * Pobiera stronę (z kontrolą SSRF przy każdym przekierowaniu).
     *
     * @return dokument HTML i końcowy adres
     * @throws OfferImportException gdy strony nie da się pobrać
     */
    FetchedPage fetch(URI uri);

    record FetchedPage(Document document, String finalUrl) {
    }
}

package pl.radarpracy.aggregator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pobieranie zasobów z sieci z zachowaniem etykiety (robots.txt, limit na domenę, User-Agent). */
public interface HttpFetcher {

    /**
     * Pobiera zasób (z obsługą przekierowań). Rzuca {@link SourceException} z komunikatem po polsku,
     * gdy adres jest niedozwolony, robots.txt zabrania pobrania, serwer zwróci błąd albo minie limit czasu.
     */
    FetchResult fetch(String url, String accept);

    /**
     * Pobrany zasób.
     *
     * @param url         adres końcowy (po przekierowaniach)
     * @param status      kod HTTP
     * @param contentType nagłówek Content-Type (może być null)
     * @param body        treść
     */
    record FetchResult(String url, int status, String contentType, byte[] body) {

        private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9._-]+)");

        /** Kodowanie z nagłówka Content-Type albo null. */
        public String charset() {
            if (contentType == null) {
                return null;
            }
            Matcher m = CHARSET.matcher(contentType);
            return m.find() ? m.group(1) : null;
        }

        public String text() {
            Charset cs = StandardCharsets.UTF_8;
            String name = charset();
            if (name != null) {
                try {
                    cs = Charset.forName(name);
                } catch (RuntimeException ignored) {
                    // nieznane kodowanie — UTF-8
                }
            }
            return new String(body, cs);
        }

        public boolean looksLikeXml() {
            String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("xml")) {
                return true;
            }
            String head = new String(body, 0, Math.min(body.length, 512), StandardCharsets.ISO_8859_1).stripLeading();
            if (head.startsWith("﻿") || head.startsWith("ï»¿")) {
                head = head.substring(head.startsWith("﻿") ? 1 : 3).stripLeading();
            }
            return head.startsWith("<?xml") || head.startsWith("<rss") || head.startsWith("<feed")
                    || head.startsWith("<rdf:RDF");
        }
    }
}

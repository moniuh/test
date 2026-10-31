package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Nazwa źródła oferty na podstawie hosta adresu URL (np. „pracuj.pl”, „LinkedIn”). */
public final class SourceResolver {

    private static final Map<String, String> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("pracuj.pl", "pracuj.pl");
        KNOWN.put("justjoin.it", "justjoin.it");
        KNOWN.put("nofluffjobs.com", "nofluffjobs.com");
        KNOWN.put("linkedin.com", "LinkedIn");
        KNOWN.put("olx.pl", "olx.pl");
        KNOWN.put("theprotocol.it", "theprotocol.it");
        KNOWN.put("bulldogjob.pl", "bulldogjob.pl");
        KNOWN.put("rocketjobs.pl", "rocketjobs.pl");
        KNOWN.put("indeed.com", "Indeed");
        KNOWN.put("glassdoor.com", "Glassdoor");
        KNOWN.put("praca.pl", "praca.pl");
        KNOWN.put("gowork.pl", "gowork.pl");
        KNOWN.put("solid.jobs", "solid.jobs");
        KNOWN.put("inhire.io", "inhire.io");
        KNOWN.put("aplikuj.pl", "aplikuj.pl");
        KNOWN.put("infopraca.pl", "infopraca.pl");
        KNOWN.put("jooble.org", "Jooble");
        KNOWN.put("facebook.com", "Facebook");
    }

    private SourceResolver() {
    }

    /** Źródło z adresu: znany portal albo host bez „www.”. Pusty wynik dla niepoprawnego adresu. */
    public static Optional<String> fromUrl(String url) {
        if (Text.isBlank(url)) {
            return Optional.empty();
        }
        String host;
        try {
            String u = url.strip();
            if (!u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
                u = "https://" + u;
            }
            host = URI.create(u).getHost();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        for (var e : KNOWN.entrySet()) {
            String domain = e.getKey();
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return Optional.of(e.getValue());
            }
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        return Optional.of(host);
    }
}

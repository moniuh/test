package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Ochrona przed SSRF: dopuszcza wyłącznie adresy http/https, których host po rozwiązaniu DNS wskazuje
 * na publiczne adresy IP. Odrzuca localhost, sieci prywatne (10/8, 172.16/12, 192.168/16, fc00::/7),
 * link-local (169.254/16 — m.in. metadane chmury, fe80::/10), CGNAT (100.64/10), multicast, „0.0.0.0” itp.
 */
public class UrlGuard {

    /** Rozwiązywanie nazw (podmieniane w testach). */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;
    private final boolean allowLoopback;

    public UrlGuard() {
        this(InetAddress::getAllByName, false);
    }

    /**
     * @param resolver      rozwiązywanie nazw
     * @param allowLoopback tylko do testów z lokalnym serwerem HTTP — w aplikacji zawsze false
     */
    public UrlGuard(Resolver resolver, boolean allowLoopback) {
        this.resolver = resolver;
        this.allowLoopback = allowLoopback;
    }

    /** Sprawdzenie składni i schematu (bez DNS): http/https, host, bez danych logowania. */
    public static URI parseHttpUrl(String raw) {
        if (Text.isBlank(raw)) {
            throw new SourceException("Podaj adres URL źródła");
        }
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException e) {
            throw new SourceException("Niepoprawny adres URL: " + e.getReason());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new SourceException("Dozwolone są tylko adresy http:// i https://");
        }
        if (Text.isBlank(uri.getHost())) {
            throw new SourceException("Adres URL nie zawiera nazwy hosta");
        }
        if (uri.getRawUserInfo() != null) {
            throw new SourceException("Adres URL nie może zawierać danych logowania");
        }
        return uri;
    }

    /**
     * Wstępna kontrola przy zapisie źródła (bez zapytań DNS): składnia, schemat i oczywiste adresy
     * lokalne (localhost, literały IP z sieci prywatnych).
     */
    public URI checkStatic(String raw) {
        URI uri = parseHttpUrl(raw);
        String host = hostOf(uri);
        if (isLocalName(host)) {
            throw blocked(host);
        }
        if (isIpLiteral(host)) {
            try {
                for (InetAddress a : InetAddress.getAllByName(host)) {
                    if (!isPublic(a)) {
                        throw blocked(host);
                    }
                }
            } catch (UnknownHostException e) {
                throw new SourceException("Niepoprawny adres IP: " + host);
            }
        }
        return uri;
    }

    /** Pełna kontrola przed połączeniem: rozwiązanie DNS i sprawdzenie wszystkich adresów hosta. */
    public URI check(String raw) {
        URI uri = parseHttpUrl(raw);
        String host = hostOf(uri);
        if (isLocalName(host)) {
            throw blocked(host);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new SourceException("Nie można odnaleźć hosta „" + host + "” (DNS)");
        }
        if (addresses == null || addresses.length == 0) {
            throw new SourceException("Nie można odnaleźć hosta „" + host + "” (DNS)");
        }
        for (InetAddress a : addresses) {
            if (!isPublic(a)) {
                throw blocked(host);
            }
        }
        return uri;
    }

    private SourceException blocked(String host) {
        return new SourceException("Adres „" + host + "” wskazuje na sieć lokalną lub prywatną — "
                + "agregator pobiera wyłącznie z publicznych adresów (ochrona SSRF)");
    }

    private boolean isLocalName(String host) {
        if (allowLoopback && (host.equals("localhost") || host.equals("127.0.0.1"))) {
            return false;
        }
        return host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.endsWith(".internal") || host.equals("0") || host.equals("0.0.0.0");
    }

    static String hostOf(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("[0-9.]+") || host.contains(":");
    }

    /** Czy adres jest publiczny (routowalny w internecie). */
    boolean isPublic(InetAddress a) {
        if (a.isLoopbackAddress()) {
            return allowLoopback;
        }
        if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return isPublicV4(b);
        }
        if (a instanceof Inet6Address) {
            // IPv4-mapped (::ffff:a.b.c.d) i IPv4-compatible
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && ((b[10] == (byte) 0xff && b[11] == (byte) 0xff) || (b[10] == 0 && b[11] == 0))) {
                byte[] v4 = {b[12], b[13], b[14], b[15]};
                return isPublicV4(v4) && !(b[10] == 0 && v4[0] == 0);
            }
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false; // fc00::/7 unique local
            }
            if (first == 0xfe && (b[1] & 0xc0) == 0xc0) {
                return false; // fec0::/10 site-local (przestarzałe)
            }
            if (first == 0x20 && (b[1] & 0xff) == 0x01 && b[2] == 0x0d && (b[3] & 0xff) == 0xb8) {
                return false; // 2001:db8::/32 dokumentacja
            }
            if (first == 0x00) {
                return false; // ::/8 zarezerwowane
            }
            return true;
        }
        return false;
    }

    private static boolean isPublicV4(byte[] b) {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        if (a0 == 0 || a0 == 10 || a0 == 127) {
            return false;
        }
        if (a0 == 100 && a1 >= 64 && a1 <= 127) {
            return false; // CGNAT 100.64/10
        }
        if (a0 == 169 && a1 == 254) {
            return false; // link-local, metadane chmury
        }
        if (a0 == 172 && a1 >= 16 && a1 <= 31) {
            return false;
        }
        if (a0 == 192 && a1 == 168) {
            return false;
        }
        if (a0 == 192 && a1 == 0 && (b[2] & 0xff) == 0) {
            return false; // 192.0.0.0/24
        }
        if (a0 == 198 && (a1 == 18 || a1 == 19)) {
            return false; // testy wydajności
        }
        return a0 < 224; // multicast i zarezerwowane 224+
    }
}

package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Ochrona przed SSRF przy imporcie oferty z adresu (#14): dopuszcza tylko http/https na standardowych
 * portach i wyłącznie hosty, których <em>wszystkie</em> adresy po rozwiązaniu DNS są publiczne
 * (odrzuca localhost, sieci prywatne, link-local, CGNAT, multicast, adresy zarezerwowane, IPv6 ULA).
 *
 * <p>Sprawdzenie jest powtarzane dla każdego przekierowania. Ograniczenie: między sprawdzeniem a
 * połączeniem DNS może zwrócić inny adres (DNS rebinding) — dlatego dodatkowo nie podążamy
 * automatycznie za przekierowaniami i nie wysyłamy żadnych ciasteczek ani nagłówków uwierzytelniania.</p>
 */
public final class UrlGuard {

    /** Rozwiązywanie nazw — podmieniane w testach. */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static final Resolver SYSTEM_DNS = InetAddress::getAllByName;

    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443, 8080, 8443);
    private static final Set<String> BLOCKED_SUFFIXES = Set.of(
            ".localhost", ".local", ".internal", ".lan", ".home.arpa", ".intranet", ".corp");

    private UrlGuard() {
    }

    /** Waliduje adres i zwraca znormalizowany URI; rzuca {@link BadRequestException} z opisem po polsku. */
    public static URI validate(String url, Resolver resolver) {
        if (Text.isBlank(url)) {
            throw new BadRequestException("Podaj adres ogłoszenia (URL)");
        }
        String u = url.strip();
        if (u.length() > 2000) {
            throw new BadRequestException("Adres jest za długi");
        }
        URI uri;
        try {
            uri = new URI(u);
        } catch (URISyntaxException e) {
            throw new BadRequestException("Niepoprawny adres URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException("Obsługujemy tylko adresy http:// i https://");
        }
        if (uri.getRawUserInfo() != null) {
            throw new BadRequestException("Adres nie może zawierać nazwy użytkownika ani hasła");
        }
        String host = uri.getHost();
        if (Text.isBlank(host)) {
            throw new BadRequestException("Adres nie zawiera nazwy serwera");
        }
        if (!ALLOWED_PORTS.contains(uri.getPort())) {
            throw new BadRequestException("Dozwolone są tylko standardowe porty (80, 443, 8080, 8443)");
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (!h.contains(":")) {
            try {
                h = IDN.toASCII(h);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Niepoprawna nazwa serwera");
            }
        }
        if (h.equals("localhost") || BLOCKED_SUFFIXES.stream().anyMatch(h::endsWith) || !h.contains(".") && !h.contains(":")) {
            throw blocked();
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(h);
        } catch (UnknownHostException e) {
            throw new BadRequestException("Nie znaleziono serwera „" + host + "” — sprawdź adres");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BadRequestException("Nie znaleziono serwera „" + host + "” — sprawdź adres");
        }
        for (InetAddress a : addresses) {
            if (isForbidden(a)) {
                throw blocked();
            }
        }
        return uri;
    }

    private static BadRequestException blocked() {
        return new BadRequestException(
                "Ze względów bezpieczeństwa nie pobieramy stron z sieci lokalnej ani adresów prywatnych");
    }

    /** Czy adres należy do zakresu niepublicznego. */
    public static boolean isForbidden(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return isForbiddenV4(b);
        }
        if (a instanceof Inet6Address) {
            int first = b[0] & 0xff;
            // fc00::/7 — unikalne adresy lokalne (ULA)
            if ((first & 0xfe) == 0xfc) {
                return true;
            }
            // 2001:db8::/32 — dokumentacja
            if (first == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
                return true;
            }
            // ::ffff:a.b.c.d (IPv4-mapped), ::a.b.c.d (IPv4-compatible), 64:ff9b::/96 (NAT64) — sprawdź osadzony IPv4
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) {
                zeroPrefix &= b[i] == 0;
            }
            boolean mapped = zeroPrefix && ((b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff || b[10] == 0 && b[11] == 0);
            boolean nat64 = (b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
            if (mapped || nat64) {
                byte[] v4 = {b[12], b[13], b[14], b[15]};
                return isForbiddenV4(v4);
            }
            return false;
        }
        return true;
    }

    private static boolean isForbiddenV4(byte[] b) {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        int a2 = b[2] & 0xff;
        return a0 == 0                                   // 0.0.0.0/8
                || a0 == 10                              // 10.0.0.0/8
                || a0 == 127                             // loopback
                || a0 == 100 && a1 >= 64 && a1 <= 127    // 100.64.0.0/10 (CGNAT)
                || a0 == 169 && a1 == 254                // link-local (m.in. metadane chmury 169.254.169.254)
                || a0 == 172 && a1 >= 16 && a1 <= 31     // 172.16.0.0/12
                || a0 == 192 && a1 == 168                // 192.168.0.0/16
                || a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2) // 192.0.0.0/24, 192.0.2.0/24
                || a0 == 198 && (a1 == 18 || a1 == 19)   // 198.18.0.0/15 (testy wydajności)
                || a0 == 198 && a1 == 51 && a2 == 100    // 198.51.100.0/24
                || a0 == 203 && a1 == 0 && a2 == 113     // 203.0.113.0/24
                || a0 >= 224;                            // multicast, 240.0.0.0/4, broadcast
    }
}

package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.radarpracy.common.BadRequestException;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ochrona SSRF importu z URL (#14) — z podmienionym DNS, bez sieci. */
class UrlGuardTest {

    private static UrlGuard.Resolver resolvesTo(String ip) {
        return host -> new InetAddress[]{InetAddress.getByName(ip)};
    }

    @Test
    void acceptsPublicHttpAndHttps() {
        assertThat(UrlGuard.validate("https://www.pracuj.pl/praca/x,oferta,1", resolvesTo("93.184.215.14")).getHost())
                .isEqualTo("www.pracuj.pl");
        assertThat(UrlGuard.validate(" http://justjoin.it:443/offers/a ", resolvesTo("2606:4700::6810:84e5"))).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/a", "file:///etc/passwd", "javascript:alert(1)", "gopher://x.pl/",
            "example.com/oferta", "https://user:pass@example.com/", "https://example.com:22/", "", "http://"})
    void rejectsUnsupportedOrMalformed(String url) {
        assertThatThrownBy(() -> UrlGuard.validate(url, resolvesTo("93.184.215.14")))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.10", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1", "::1", "fe80::1", "fc00::1",
            "fd12:3456::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1"})
    void rejectsPrivateAddressesAfterDnsResolution(String ip) {
        assertThatThrownBy(() -> UrlGuard.validate("https://oferty.example.com/1", resolvesTo(ip)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sieci lokalnej");
    }

    @Test
    void rejectsIfAnyResolvedAddressIsPrivate() {
        UrlGuard.Resolver mixed = host -> new InetAddress[]{InetAddress.getByName("93.184.215.14"),
                InetAddress.getByName("10.0.0.7")};
        assertThatThrownBy(() -> UrlGuard.validate("https://rebind.example.com/", mixed))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/api", "http://LOCALHOST/", "http://app.localhost/",
            "http://printer.local/", "http://intranet/", "http://2130706433/", "http://[::1]/", "http://127.0.0.1/"})
    void rejectsLocalHostnamesAndLiterals(String url) {
        assertThatThrownBy(() -> UrlGuard.validate(url, UrlGuard.SYSTEM_DNS)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void unknownHostIsReportedClearly() {
        UrlGuard.Resolver none = host -> {
            throw new UnknownHostException(host);
        };
        assertThatThrownBy(() -> UrlGuard.validate("https://nie-ma-takiej-domeny.example/", none))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Nie znaleziono serwera");
    }

    @Test
    void publicAddressesAreNotForbidden() throws Exception {
        assertThat(UrlGuard.isForbidden(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(UrlGuard.isForbidden(InetAddress.getByName("2a00:1450:4001::200e"))).isFalse();
        assertThat(UrlGuard.isForbidden(InetAddress.getByName("172.32.0.1"))).isFalse();
    }
}

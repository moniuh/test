package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTest {

    private static final String UA = "RadarPracy/0.2 (osobisty agregator ofert pracy)";

    @Test
    void wildcardGroupWithAllowAndDisallow() {
        RobotsTxt robots = RobotsTxt.parse("""
                # komentarz
                User-agent: *
                Disallow: /admin
                Disallow: /kariera/wewnetrzne/
                Allow: /kariera/wewnetrzne/publiczne
                Disallow: /*.pdf$
                """);
        assertThat(robots.isAllowed(UA, "/kariera")).isTrue();
        assertThat(robots.isAllowed(UA, "/admin/panel")).isFalse();
        assertThat(robots.isAllowed(UA, "/kariera/wewnetrzne/lista")).isFalse();
        assertThat(robots.isAllowed(UA, "/kariera/wewnetrzne/publiczne/1")).isTrue();
        assertThat(robots.isAllowed(UA, "/pliki/oferta.pdf")).isFalse();
        assertThat(robots.isAllowed(UA, "/pliki/oferta.pdf?x=1")).isTrue();
        assertThat(robots.isAllowed(UA, "/robots.txt")).isTrue();
    }

    @Test
    void specificGroupForOurAgentWinsOverWildcard() {
        RobotsTxt robots = RobotsTxt.parse("""
                User-agent: *
                Disallow: /

                User-agent: Googlebot
                User-agent: RadarPracy
                Allow: /kariera
                Disallow: /
                """);
        assertThat(robots.isAllowed(UA, "/kariera/java")).isTrue();
        assertThat(robots.isAllowed(UA, "/blog")).isFalse();
        assertThat(robots.isAllowed("InnyBot/1.0", "/kariera/java")).isFalse();
    }

    @Test
    void emptyDisallowAllowsEverythingAndLongestMatchWins() {
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow:\n").isAllowed(UA, "/cokolwiek")).isTrue();
        RobotsTxt robots = RobotsTxt.parse("User-agent: *\nAllow: /oferty/\nDisallow: /oferty/archiwum\n");
        assertThat(robots.isAllowed(UA, "/oferty/123")).isTrue();
        assertThat(robots.isAllowed(UA, "/oferty/archiwum/1")).isFalse();
        assertThat(RobotsTxt.allowAll().isAllowed(UA, "/x")).isTrue();
        assertThat(RobotsTxt.disallowAll().isAllowed(UA, "/x")).isFalse();
        assertThat(RobotsTxt.productToken(UA)).isEqualTo("radarpracy");
    }
}

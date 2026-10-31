package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedParserTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Oferty</title>
                <item>
                  <title>Java Developer — Acme Soft — Kraków</title>
                  <link>https://jobs.example.com/o/1?utm_source=rss</link>
                  <guid isPermaLink="false">job-1</guid>
                  <pubDate>Mon, 26 Oct 2026 09:00:00 +0100</pubDate>
                  <category>IT</category>
                  <category>Java</category>
                  <description><![CDATA[<p>Szukamy programisty.</p><h3>Wymagania</h3><ul><li>Java 17</li><li>Spring Boot</li></ul>]]></description>
                </item>
                <item>
                  <title>Frontend Developer @ Beta Labs (Warszawa)</title>
                  <link>/oferty/2</link>
                  <guid>https://jobs.example.com/oferty/2</guid>
                  <dc:creator>Beta Labs</dc:creator>
                  <pubDate>Tue, 3 Nov 2026 10:00:00 GMT</pubDate>
                  <description>krótki opis</description>
                  <content:encoded><![CDATA[<p>Długi opis z <b>Reactem</b> i TypeScriptem.</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
            """;

    @Test
    void parsesRss2WithTitleSplitHtmlToTextAndDates() {
        FeedParser.Feed feed = FeedParser.parse(RSS, "https://jobs.example.com/feed.xml");

        assertThat(feed.format()).isEqualTo(FeedParser.Format.RSS);
        assertThat(feed.title()).isEqualTo("Oferty");
        assertThat(feed.items()).hasSize(2);

        FeedItem first = feed.items().get(0);
        assertThat(first.title()).isEqualTo("Java Developer");
        assertThat(first.company()).isEqualTo("Acme Soft");
        assertThat(first.location()).isEqualTo("Kraków");
        assertThat(first.externalId()).isEqualTo("job-1");
        assertThat(first.url()).isEqualTo("https://jobs.example.com/o/1?utm_source=rss");
        assertThat(first.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(first.categories()).containsExactly("IT", "Java");
        assertThat(first.description()).contains("Szukamy programisty.").contains("Wymagania\n- Java 17\n- Spring Boot");

        FeedItem second = feed.items().get(1);
        assertThat(second.title()).isEqualTo("Frontend Developer");
        assertThat(second.company()).isEqualTo("Beta Labs");
        assertThat(second.location()).isEqualTo("Warszawa");
        assertThat(second.url()).isEqualTo("https://jobs.example.com/oferty/2");
        assertThat(second.description()).isEqualTo("Długi opis z Reactem i TypeScriptem.");
        assertThat(second.publishedAt()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void parsesAtomEntries() {
        String atom = """
                <?xml version="1.0" encoding="utf-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Kariera Gamma</title>
                  <entry>
                    <title type="text">Analityk danych | Gamma S.A. | Gdańsk</title>
                    <link rel="self" href="https://gamma.example/api/1"/>
                    <link rel="alternate" href="https://gamma.example/kariera/1"/>
                    <id>urn:gamma:job:1</id>
                    <updated>2026-10-20T08:00:00Z</updated>
                    <published>2026-10-19T08:00:00Z</published>
                    <author><name>Dział HR</name></author>
                    <category term="analityka" label="Analityka"/>
                    <summary>Krótko</summary>
                    <content type="html">&lt;p&gt;SQL i &lt;b&gt;Power BI&lt;/b&gt;&lt;/p&gt;</content>
                  </entry>
                  <entry>
                    <title>Tester</title>
                    <link href="https://gamma.example/kariera/2"/>
                    <id>urn:gamma:job:2</id>
                    <updated>2026-10-21T08:00:00Z</updated>
                    <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Testy</p><ul><li>Selenium</li></ul></div></content>
                  </entry>
                </feed>
                """;
        FeedParser.Feed feed = FeedParser.parse(atom, null);

        assertThat(feed.format()).isEqualTo(FeedParser.Format.ATOM);
        assertThat(feed.items()).hasSize(2);
        FeedItem a = feed.items().get(0);
        assertThat(a.title()).isEqualTo("Analityk danych");
        assertThat(a.company()).isEqualTo("Gamma S.A.");
        assertThat(a.location()).isEqualTo("Gdańsk");
        assertThat(a.url()).isEqualTo("https://gamma.example/kariera/1");
        assertThat(a.externalId()).isEqualTo("urn:gamma:job:1");
        assertThat(a.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(a.description()).isEqualTo("SQL i Power BI");
        assertThat(a.categories()).containsExactly("Analityka");

        FeedItem b = feed.items().get(1);
        assertThat(b.url()).isEqualTo("https://gamma.example/kariera/2");
        assertThat(b.description()).contains("Testy").contains("- Selenium");
    }

    @Test
    void rejectsXxeAttempt() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel><title>x</title>
                <item><title>&xxe;</title><link>https://a.example/1</link></item>
                </channel></rss>
                """;
        assertThatThrownBy(() -> FeedParser.parse(xxe, null))
                .isInstanceOf(SourceException.class)
                .hasMessageContaining("DOCTYPE")
                .hasMessageContaining("XXE");
    }

    @Test
    void rejectsBillionLaughsAndNonFeedXml() {
        String laughs = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;">]>
                <rss><channel><item><title>&lol2;</title></item></channel></rss>
                """;
        assertThatThrownBy(() -> FeedParser.parse(laughs, null)).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> FeedParser.parse("<html><body>nie kanał</body></html>", null))
                .isInstanceOf(SourceException.class).hasMessageContaining("nie jest kanał");
        assertThatThrownBy(() -> FeedParser.parse("to nie jest xml", null)).isInstanceOf(SourceException.class);
    }

    @Test
    void ignoresNonHttpLinksAndGeneratesStableIdWithoutGuid() {
        String rss = """
                <rss version="2.0"><channel>
                <item><title>Kierowca kat. C</title><link>javascript:alert(1)</link><description>Opis</description></item>
                </channel></rss>
                """;
        FeedItem item = FeedParser.parse(rss, null).items().get(0);
        assertThat(item.url()).isNull();
        assertThat(item.externalId()).startsWith("sha:");
        assertThat(FeedParser.parse(rss, null).items().get(0).externalId()).isEqualTo(item.externalId());
    }

    @Test
    void titleSplitterHeuristics() {
        assertThat(TitleSplitter.split("Specjalista ds. kadr - Firma XYZ Sp. z o.o. - Warszawa"))
                .isEqualTo(new TitleSplitter.Parts("Specjalista ds. kadr", "Firma XYZ Sp. z o.o.", "Warszawa"));
        assertThat(TitleSplitter.split("Senior Engineer at Acme"))
                .isEqualTo(new TitleSplitter.Parts("Senior Engineer", "Acme", null));
        assertThat(TitleSplitter.split("Java Developer - Senior"))
                .isEqualTo(new TitleSplitter.Parts("Java Developer - Senior", null, null));
        assertThat(TitleSplitter.split("Fullstack Developer (Java + React)"))
                .isEqualTo(new TitleSplitter.Parts("Fullstack Developer (Java + React)", null, null));
        assertThat(TitleSplitter.split("Tester — Kraków"))
                .isEqualTo(new TitleSplitter.Parts("Tester", null, "Kraków"));
        assertThat(TitleSplitter.split("Programista PHP — Wrocław — Webowo"))
                .isEqualTo(new TitleSplitter.Parts("Programista PHP", "Webowo", "Wrocław"));
    }

    @Test
    void htmlToTextKeepsStructureAndDropsHiddenElements() {
        String html = "<div><h2>Wymagania</h2><ul><li>Java</li><li>SQL</li></ul>"
                + "<p>Linia 1<br>Linia 2</p><span style='display: none'>ukryty tekst dla AI</span>"
                + "<script>alert(1)</script></div>";
        assertThat(HtmlText.toText(html)).isEqualTo("Wymagania\n- Java\n- SQL\nLinia 1\nLinia 2");
        assertThat(HtmlText.hiddenTexts(html)).extracting(HtmlText.HiddenText::text).containsExactly("ukryty tekst dla AI");
        assertThat(HtmlText.toText("Zwykły tekst\n\n\nbez HTML")).isEqualTo("Zwykły tekst\n\nbez HTML");
        // biały tekst na ciemnym tle przodka jest widoczny
        String banner = "<div style=\"background-color:#1c2033\"><p><span style=\"color:#fff\">Dołącz do nas!</span></p></div>";
        assertThat(HtmlText.hiddenTexts(banner)).isEmpty();
        assertThat(HtmlText.toText(banner)).isEqualTo("Dołącz do nas!");
    }

    @Test
    void parsesVariousDateFormats() {
        assertThat(Dates.parse("Sat, 31 Oct 2026 12:00:00 GMT")).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(Dates.parse("2026-10-30")).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(Dates.parse("05.11.2026")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(Dates.parse("Thu, 5 Nov 2026 10:00:00 PST")).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(Dates.parse("jutro")).isNull();
    }
}

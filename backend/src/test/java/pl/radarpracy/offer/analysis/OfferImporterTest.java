package pl.radarpracy.offer.analysis;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.skills.SkillDictionary;

import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Import z adresu (#14) — testy na statycznym HTML, bez sieci. */
class OfferImporterTest {

    private final OfferTextParser parser = new OfferTextParser(new RequirementExtractor(new SkillDictionary()));
    private final UrlGuard.Resolver publicDns = host -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{93, (byte) 184, (byte) 215, 14})};
    private final List<URI> fetched = new ArrayList<>();

    private OfferImporter importer(String html) {
        PageFetcher fetcher = uri -> {
            fetched.add(uri);
            return new PageFetcher.FetchedPage(Jsoup.parse(html, uri.toString()), uri.toString());
        };
        return new OfferImporter(parser, fetcher, publicDns);
    }

    static final String JSON_LD_PAGE = """
            <!doctype html>
            <html lang="pl"><head>
            <title>Senior Java Developer | Portal</title>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"BreadcrumbList","itemListElement":[]},
              {"@type":"JobPosting",
               "title":"Senior Java Developer",
               "hiringOrganization":{"@type":"Organization","name":"FinPay Systems"},
               "jobLocation":[{"@type":"Place","address":{"@type":"PostalAddress","addressLocality":"Warszawa","addressCountry":"PL"}}],
               "employmentType":["CONTRACTOR"],
               "datePosted":"2026-10-20",
               "validThrough":"2026-11-30T23:59:59+01:00",
               "baseSalary":{"@type":"MonetaryAmount","currency":"PLN","value":{"@type":"QuantitativeValue","minValue":22000,"maxValue":"28000","unitText":"MONTH"}},
               "description":"<p>Budujemy platformę płatności natychmiastowych dla banków.</p><h3>Wymagania:</h3><ul><li>Minimum 5 lat doświadczenia w Java</li><li>Spring Boot</li><li>Apache Kafka</li></ul><h3>Mile widziane:</h3><ul><li>AWS</li></ul><h3>Oferujemy:</h3><ul><li>Prywatną opiekę medyczną</li><li>Pracę hybrydową (2 dni w biurze)</li></ul>"}
            ]}
            </script>
            </head><body>
            <nav><a href="/">Strona główna</a> <a href="/oferty">Oferty pracy</a></nav>
            <main><h1>Senior Java Developer</h1><p>Treść ogłoszenia wczytywana skryptem…</p></main>
            <footer>© Portal</footer>
            </body></html>
            """;

    static final String PLAIN_PAGE = """
            <html><head><title>Oferta</title><script>var tracking = "Wymagania: nic";</script></head>
            <body>
            <header><a href="/">Logo</a><nav><ul><li>Praca</li><li>Firmy</li><li>Zaloguj</li></ul></nav></header>
            <div class="cookie-banner">Ta strona używa ciasteczek. Akceptuj</div>
            <article>
              <h1>Frontend Developer (React)</h1>
              <p>Sklepik.pl — Poznań, praca zdalna</p>
              <p>Umowa o pracę: 11&nbsp;000 – 15&nbsp;000 zł brutto miesięcznie</p>
              <h2>Wymagania</h2>
              <ul><li>3 lata doświadczenia z React</li><li>TypeScript</li><li>Redux</li></ul>
              <h2>Oferujemy</h2>
              <ul><li>Karta sportowa</li><li>Budżet na sprzęt</li></ul>
            </article>
            <aside>Podobne oferty: Java Developer, Python Developer</aside>
            <footer>Regulamin · Polityka prywatności</footer>
            </body></html>
            """;

    @Test
    void jsonLdJobPostingHasPriority() {
        ParsedOffer p = importer(JSON_LD_PAGE).importUrl("https://justjoin.it/offers/finpay-senior-java");
        OfferDraft d = p.draft();
        assertThat(p.origin()).isEqualTo("json-ld");
        assertThat(d.title()).isEqualTo("Senior Java Developer");
        assertThat(d.company()).isEqualTo("FinPay Systems");
        assertThat(d.location()).isEqualTo("Warszawa");
        assertThat(d.salaryMin()).isEqualTo(22000);
        assertThat(d.salaryMax()).isEqualTo(28000);
        assertThat(d.salaryCurrency()).isEqualTo("PLN");
        assertThat(d.salaryPeriod()).isEqualTo(SalaryPeriod.MONTH);
        assertThat(d.contractTypes()).containsExactly(ContractType.B2B);
        assertThat(d.workMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(d.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(d.expiresAt()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(d.requirements()).extracting(r -> r.text())
                .containsExactly("Minimum 5 lat doświadczenia w Java", "Spring Boot", "Apache Kafka", "AWS");
        assertThat(d.benefits()).containsExactly("Prywatną opiekę medyczną", "Pracę hybrydową (2 dni w biurze)");
        assertThat(d.source()).isEqualTo("justjoin.it");
        assertThat(d.description()).contains("Budujemy platformę").doesNotContain("<p>");
        assertThat(p.fields()).filteredOn(f -> f.field().equals("company")).first()
                .satisfies(f -> assertThat(f.method()).contains("JSON-LD"));
        assertThat(p.warnings()).noneMatch(w -> w.contains("JobPosting"));
        assertThat(fetched).hasSize(1);
    }

    @Test
    void withoutJsonLdUsesMainContentWithoutNavigationAndFooter() {
        ParsedOffer p = importer(PLAIN_PAGE).process(PLAIN_PAGE, "https://nofluffjobs.com/pl/job/frontend-sklepik");
        OfferDraft d = p.draft();
        assertThat(p.origin()).isEqualTo("html");
        assertThat(d.title()).isEqualTo("Frontend Developer (React)");
        assertThat(d.company()).isEqualTo("Sklepik.pl");
        assertThat(d.location()).isEqualTo("Poznań");
        assertThat(d.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(d.contractTypes()).containsExactly(ContractType.UOP);
        assertThat(d.salaryMin()).isEqualTo(11000);
        assertThat(d.requirements()).extracting(r -> r.text())
                .containsExactly("3 lata doświadczenia z React", "TypeScript", "Redux");
        assertThat(d.benefits()).containsExactly("Karta sportowa", "Budżet na sprzęt");
        assertThat(d.description()).doesNotContain("ciasteczek", "Regulamin", "Zaloguj", "Podobne oferty", "tracking");
        assertThat(p.warnings()).anyMatch(w -> w.contains("JobPosting"));
    }

    @Test
    void htmlToTextKeepsStructure() {
        String text = HtmlText.toText(Jsoup.parse("<div><h2>Wymagania:</h2><ul><li>Java</li><li>SQL<br>(Oracle)</li></ul>"
                + "<p>Pierwszy   akapit</p><p>Drugi&nbsp;akapit</p></div>").body());
        assertThat(text).isEqualTo("Wymagania:\n- Java\n- SQL\n(Oracle)\n\nPierwszy akapit\n\nDrugi akapit");
    }

    @Test
    void jsonLdVariants() {
        String html = """
                <script type="application/ld+json">{ broken json </script>
                <script type="application/ld+json">
                [{"@type":"Organization","name":"X"},
                 {"@type":["JobPosting"],"title":"Data Engineer","hiringOrganization":"Acme S.A.",
                  "jobLocationType":"TELECOMMUTE","employmentType":"INTERN",
                  "baseSalary":{"currency":"EUR","value":{"value":40,"unitText":"HOUR"}},
                  "description":"Opis stanowiska"}]
                </script>
                """;
        JobPostingData d = JobPostingExtractor.extract(Jsoup.parse(html)).orElseThrow();
        assertThat(d.title()).isEqualTo("Data Engineer");
        assertThat(d.company()).isEqualTo("Acme S.A.");
        assertThat(d.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(d.contractTypes()).containsExactly(ContractType.INTERNSHIP);
        assertThat(d.salaryMin()).isEqualTo(40);
        assertThat(d.salaryMax()).isNull();
        assertThat(d.salaryCurrency()).isEqualTo("EUR");
        assertThat(d.salaryPeriod()).isEqualTo(SalaryPeriod.HOUR);
        assertThat(JobPostingExtractor.extract(Jsoup.parse("<p>bez danych</p>"))).isEmpty();
    }

    @Test
    void pageWithoutContentFailsWithHint() {
        String html = "<html><body><div id='app'></div><script>render()</script></body></html>";
        assertThatThrownBy(() -> importer(html).importUrl("https://example.com/oferta/1"))
                .isInstanceOf(OfferImportException.class)
                .satisfies(e -> assertThat(((OfferImportException) e).reason()).isEqualTo("empty"));
    }

    @Test
    void unsafeUrlIsRejectedBeforeFetching() {
        OfferImporter imp = new OfferImporter(parser, uri -> {
            throw new AssertionError("nie powinno pobierać");
        }, host -> new InetAddress[]{InetAddress.getByAddress(host, new byte[]{10, 0, 0, 5})});
        assertThatThrownBy(() -> imp.importUrl("https://intranet.example.com/oferta"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sieci lokalnej");
    }
}

package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DedupKeysTest {

    @Test
    void canonicalUrlDropsTrackingFragmentAndTrailingSlash() {
        assertThat(DedupKeys.canonicalUrl("https://WWW.Jobs.example.com/oferta/12/?utm_source=rss&id=5&fbclid=x#apply"))
                .isEqualTo("https://jobs.example.com/oferta/12?id=5");
        assertThat(DedupKeys.canonicalUrl("http://jobs.example.com/oferta/12"))
                .isEqualTo(DedupKeys.canonicalUrl("https://jobs.example.com/oferta/12/"));
        assertThat(DedupKeys.canonicalUrl(null)).isNull();
    }

    @Test
    void contentHashIgnoresCaseDiacriticsLegalFormsAndGenderMarkers() {
        String a = DedupKeys.contentHash("Księgowa (k/m)", "Biuro Rachunkowe Liczydło sp. z o.o.");
        String b = DedupKeys.contentHash("KSIEGOWA", "Biuro rachunkowe LICZYDŁO");
        assertThat(a).isEqualTo(b).hasSize(40);
        assertThat(DedupKeys.contentHash("Java Developer", "Acme S.A."))
                .isEqualTo(DedupKeys.contentHash("Java  Developer", "ACME"));
        assertThat(DedupKeys.contentHash("Java Developer", "Acme"))
                .isNotEqualTo(DedupKeys.contentHash("Java Developer", "Beta"));
        assertThat(DedupKeys.normalizedKey("C++ Developer!", "Nordlys Software AB")).isEqualTo("c++ developer|nordlys software");
    }

    @Test
    void demoPlaceholdersAndFileNamesAreSafe() {
        String out = SourceConnector.applyPlaceholders("<pubDate>{{rfc-1}}</pubDate><d>{{date+30}}</d><e>{{date}}</e>",
                LocalDate.of(2026, 10, 31));
        assertThat(out).contains("<d>2026-11-30</d>").contains("<e>2026-10-31</e>").contains("30 Oct 2026 09:00:00");
        assertThat(SourceConnector.demoFileName("classpath:aggregator/demo-feed.xml")).isEqualTo("demo-feed.xml");
        assertThatThrownBy(() -> SourceConnector.demoFileName("../application.yml")).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> SourceConnector.demoFileName("/etc/passwd")).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> SourceConnector.loadDemo("nie-ma-takiego.xml"))
                .isInstanceOf(SourceException.class).hasMessageContaining("Nie znaleziono");
    }
}

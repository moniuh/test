package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.FunnelCalculator.Funnel;
import pl.radarpracy.analytics.FunnelCalculator.FunnelStage;
import pl.radarpracy.application.ApplicationStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.analytics.Fixtures.app;

class FunnelCalculatorTest {

    static final LocalDate D = LocalDate.of(2026, 10, 1);

    private static List<AppFacts> sample() {
        return List.of(
                app(ApplicationStatus.APPLIED, D).facts(),
                app(ApplicationStatus.NO_RESPONSE, D.plusDays(1)).facts(),
                app(ApplicationStatus.REJECTED, D.plusDays(2)).facts(),           // odpowiedź (odmowa)
                app(ApplicationStatus.SCREENING, D.plusDays(3)).facts(),          // odpowiedź
                app(ApplicationStatus.INTERVIEW, D.plusDays(4)).facts(),          // rozmowa
                app(ApplicationStatus.OFFER, D.plusDays(5)).facts(),              // oferta
                app(ApplicationStatus.ACCEPTED, D.plusDays(6)).facts(),           // przyjęta
                app(ApplicationStatus.PLANNED, null).facts());                    // pomijana
    }

    @Test
    void countsAndConversionsAreMonotonic() {
        Funnel f = FunnelCalculator.compute(sample(), null, null);
        assertThat(f.stages()).extracting(FunnelStage::key)
                .containsExactly("SENT", "RESPONSE", "INTERVIEW", "OFFER", "ACCEPTED");
        assertThat(f.stages()).extracting(FunnelStage::count).containsExactly(7, 5, 3, 2, 1);
        assertThat(f.stages().get(0).conversionFromPrevious()).isNull();
        assertThat(f.stages().get(1).conversionFromPrevious()).isEqualTo(71.4);
        assertThat(f.stages().get(2).conversionFromPrevious()).isEqualTo(60.0);
        assertThat(f.stages().get(4).conversionFromStart()).isEqualTo(14.3);
        assertThat(f.plannedExcluded()).isEqualTo(1);
        assertThat(f.summary()).contains("Z 7 wysłanych aplikacji").contains("Największy odpływ");
    }

    @Test
    void dateRangeFiltersBySentDateInclusive() {
        Funnel f = FunnelCalculator.compute(sample(), D.plusDays(2), D.plusDays(4));
        assertThat(f.stages().get(0).count()).isEqualTo(3);
        assertThat(f.stages().get(2).count()).isEqualTo(1);
    }

    @Test
    void emptyRangeHasNullConversionsAndClearSummary() {
        Funnel f = FunnelCalculator.compute(sample(), D.plusYears(1), null);
        assertThat(f.stages()).allMatch(s -> s.count() == 0 && s.conversionFromStart() == null);
        assertThat(f.stages().get(1).conversionFromPrevious()).isNull();
        assertThat(f.summary()).startsWith("Brak wysłanych aplikacji");
    }

    @Test
    void smallSampleSummaryWarnsInsteadOfNamingBottleneck() {
        Funnel f = FunnelCalculator.compute(List.of(app(ApplicationStatus.APPLIED, D).facts()), null, null);
        assertThat(f.summary()).contains("orientacyjnie").doesNotContain("Największy odpływ");
    }
}

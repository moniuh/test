package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.EffectivenessCalculator.Effectiveness;
import pl.radarpracy.analytics.EffectivenessCalculator.Group;
import pl.radarpracy.application.ApplicationStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pl.radarpracy.analytics.Fixtures.app;

class EffectivenessCalculatorTest {

    static final LocalDate D = LocalDate.of(2026, 9, 1);

    /** n aplikacji z danego źródła/CV, z czego `responded` z odpowiedzią po `days` dniach. */
    private static List<AppFacts> batch(String source, String cv, int n, int responded, int days) {
        List<AppFacts> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Fixtures.App a = app(i < responded ? ApplicationStatus.SCREENING : ApplicationStatus.APPLIED, D)
                    .source(source).cv(cv);
            if (i < responded) {
                a.firstResponse(D.plusDays(days + i));
            }
            out.add(a.facts());
        }
        return out;
    }

    @Test
    void groupsBySourceAndCvWithRatesAndMedian() {
        List<AppFacts> apps = new ArrayList<>(batch("LinkedIn", "Backend", 4, 2, 3));
        apps.addAll(batch("linkedin", "Backend", 1, 0, 0)); // ta sama grupa mimo innej wielkości liter
        apps.add(app(ApplicationStatus.PLANNED, null).source("LinkedIn").facts()); // nie liczy się
        Effectiveness e = EffectivenessCalculator.compute(apps);

        assertThat(e.applications()).isEqualTo(5);
        Group g = e.bySource().get(0);
        assertThat(g.name()).isEqualTo("LinkedIn");
        assertThat(g.applications()).isEqualTo(5);
        assertThat(g.responses()).isEqualTo(2);
        assertThat(g.responseRate()).isEqualTo(40.0);
        assertThat(g.medianDaysToResponse()).isEqualTo(3.5); // 3 i 4 dni
        assertThat(g.smallSample()).isFalse();
        assertThat(e.byCv()).hasSize(1);
    }

    @Test
    void smallSampleIsFlaggedAndNoConclusion() {
        List<AppFacts> apps = new ArrayList<>(batch("pracuj.pl", "A", 4, 4, 1));
        apps.addAll(batch("LinkedIn", "B", 4, 0, 0));
        Effectiveness e = EffectivenessCalculator.compute(apps);
        assertThat(e.bySource()).allMatch(Group::smallSample);
        assertThat(e.sourceInsight().conclusive()).isFalse();
        assertThat(e.sourceInsight().text()).contains("Za mało danych");
        assertThat(e.cvInsight().conclusive()).isFalse();
    }

    @Test
    void largeDifferenceWithSufficientSamplesGivesConclusion() {
        List<AppFacts> apps = new ArrayList<>(batch("polecenie", "Backend", 10, 8, 2));
        apps.addAll(batch("pracuj.pl", "Fullstack", 10, 1, 5));
        Effectiveness e = EffectivenessCalculator.compute(apps);
        assertThat(e.sourceInsight().conclusive()).isTrue();
        assertThat(e.sourceInsight().text()).contains("„polecenie”").contains("80% vs 10%").contains("korelacja");
        assertThat(e.cvInsight().conclusive()).isTrue();
        assertThat(e.cvInsight().text()).startsWith("Wersja CV „Backend”");
    }

    @Test
    void largeDifferenceOnMinimalSamplesIsNotConclusive() {
        // 4/5 vs 1/5: różnica 60 pp, ale z ≈ 1,90 < 1,96
        List<AppFacts> apps = new ArrayList<>(batch("A", "X", 5, 4, 1));
        apps.addAll(batch("B", "X", 5, 1, 1));
        Effectiveness e = EffectivenessCalculator.compute(apps);
        assertThat(e.sourceInsight().conclusive()).isFalse();
        assertThat(e.sourceInsight().text()).contains("może być przypadkowa");
    }

    @Test
    void smallDifferenceIsReportedAsSimilar() {
        List<AppFacts> apps = new ArrayList<>(batch("A", "X", 10, 5, 1));
        apps.addAll(batch("B", "Y", 10, 4, 1));
        Effectiveness e = EffectivenessCalculator.compute(apps);
        assertThat(e.sourceInsight().conclusive()).isFalse();
        assertThat(e.sourceInsight().text()).contains("zbliżony");
    }

    @Test
    void missingNamesHaveReadableFallbacks() {
        Effectiveness e = EffectivenessCalculator.compute(List.of(app(ApplicationStatus.APPLIED, D).facts()));
        assertThat(e.bySource().get(0).name()).isEqualTo(EffectivenessCalculator.UNKNOWN_SOURCE);
        assertThat(e.byCv().get(0).name()).isEqualTo(EffectivenessCalculator.NO_CV);
        assertThat(e.bySource().get(0).medianDaysToResponse()).isNull();
    }

    @Test
    void zStatistic() {
        assertThat(EffectivenessCalculator.twoProportionZ(4, 5, 1, 5)).isCloseTo(1.897, within(0.001));
        assertThat(EffectivenessCalculator.twoProportionZ(0, 5, 0, 5)).isZero();
        assertThat(EffectivenessCalculator.median(List.of(5L, 1L, 3L))).isEqualTo(3.0);
    }
}

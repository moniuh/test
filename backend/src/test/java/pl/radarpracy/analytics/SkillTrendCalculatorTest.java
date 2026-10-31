package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.SkillTrendCalculator.OfferSkills;
import pl.radarpracy.analytics.SkillTrendCalculator.SkillTrend;
import pl.radarpracy.analytics.SkillTrendCalculator.SkillTrends;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTrendCalculatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private static OfferSkills o(int daysAgo, Set<String> must, Set<String> nice) {
        return new OfferSkills(TODAY.minusDays(daysAgo), must, nice);
    }

    private static SkillTrend find(SkillTrends t, String skill) {
        return t.skills().stream().filter(s -> s.skill().equals(skill)).findFirst().orElseThrow();
    }

    private static List<OfferSkills> sample() {
        List<OfferSkills> list = new ArrayList<>();
        // bieżące okno (30 dni): 5 ofert
        list.add(o(0, Set.of("Go"), Set.of()));
        list.add(o(1, Set.of("Java", "Kubernetes"), Set.of("AWS")));
        list.add(o(5, Set.of("Java"), Set.of("Kubernetes")));
        list.add(o(10, Set.of("Java", "Kubernetes"), Set.of()));
        list.add(o(29, Set.of("Python"), Set.of()));
        // poprzednie okno: 5 ofert
        list.add(o(30, Set.of("Java"), Set.of()));
        list.add(o(31, Set.of("Go"), Set.of()));
        list.add(o(40, Set.of("Java", "Python"), Set.of()));
        list.add(o(45, Set.of("Python"), Set.of()));
        list.add(o(59, Set.of("Java", "Python"), Set.of()));
        // poza oknami
        list.add(o(60, Set.of("Kubernetes"), Set.of()));
        return list;
    }

    @Test
    void countsSharesMustNiceAndTrend() {
        SkillTrends t = SkillTrendCalculator.compute(sample(), TODAY, 30, 10, Map.of("Java", "CV", "AWS", "profil"),
                s -> null);
        assertThat(t.offersInWindow()).isEqualTo(5);
        assertThat(t.offersInPreviousWindow()).isEqualTo(5);
        assertThat(t.comparable()).isTrue();
        assertThat(t.from()).isEqualTo(TODAY.minusDays(29));
        assertThat(t.previousTo()).isEqualTo(TODAY.minusDays(30));

        SkillTrend java = find(t, "Java");
        assertThat(java.offers()).isEqualTo(3);
        assertThat(java.share()).isEqualTo(60.0);
        assertThat(java.previousShare()).isEqualTo(60.0);
        assertThat(java.trend()).isEqualTo("STABLE");
        assertThat(java.candidateHas()).isTrue();
        assertThat(java.candidateSource()).isEqualTo("CV");

        SkillTrend k8s = find(t, "Kubernetes");
        assertThat(k8s.offers()).isEqualTo(3);
        assertThat(k8s.mustHave()).isEqualTo(2);
        assertThat(k8s.niceToHave()).isEqualTo(1);
        assertThat(k8s.trend()).isEqualTo("UP");
        assertThat(k8s.deltaPp()).isEqualTo(60.0);
        assertThat(k8s.candidateHas()).isFalse();

        SkillTrend python = find(t, "Python");
        assertThat(python.trend()).isEqualTo("DOWN");
        assertThat(python.deltaPp()).isEqualTo(-40.0);

        // sortowanie: najpierw najczęstsze, przy remisie więcej obowiązkowych
        assertThat(t.skills().get(0).skill()).isEqualTo("Java");
        assertThat(t.candidateHasCount()).isEqualTo(2);
        assertThat(t.summary()).contains("5 ofert").contains("Najczęstsze braki: Kubernetes");
    }

    @Test
    void notComparableWhenPreviousWindowTooSmall() {
        List<OfferSkills> list = List.of(o(1, Set.of("Java"), Set.of()), o(2, Set.of("Java"), Set.of()),
                o(3, Set.of("Go"), Set.of()), o(4, Set.of("Go"), Set.of()), o(5, Set.of("Go"), Set.of()),
                o(35, Set.of("Java"), Set.of()), o(36, Set.of("Java"), Set.of()), o(37, Set.of("Java"), Set.of()),
                o(38, Set.of("Java"), Set.of()));
        SkillTrends t = SkillTrendCalculator.compute(list, TODAY, 30, 10, Map.of(), s -> null);
        assertThat(t.comparable()).isFalse();
        assertThat(t.skills()).allMatch(s -> s.trend().equals("UNKNOWN") && s.deltaPp() == null);
        assertThat(t.summary()).contains("Porównanie z poprzednim okresem wymaga");
    }

    @Test
    void limitAndEmptyWindow() {
        SkillTrends t = SkillTrendCalculator.compute(sample(), TODAY, 30, 1, Map.of(), s -> null);
        assertThat(t.skills()).hasSize(1);
        SkillTrends empty = SkillTrendCalculator.compute(List.of(), TODAY, 14, 10, Map.of(), s -> null);
        assertThat(empty.skills()).isEmpty();
        assertThat(empty.summary()).startsWith("Brak ofert z ostatnich 14 dni");
    }
}

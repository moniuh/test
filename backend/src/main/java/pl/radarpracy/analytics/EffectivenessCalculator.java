package pl.radarpracy.analytics;

import pl.radarpracy.analytics.AppFacts.Stage;
import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Skuteczność wg źródła i wersji CV (pomysł #72, test A/B CV).
 *
 * <p>Zasada „bez nadinterpretacji”: wniosek pada tylko wtedy, gdy obie porównywane grupy mają co najmniej
 * {@link #MIN_SAMPLE} aplikacji, różnica odsetka odpowiedzi wynosi co najmniej {@link #MIN_DIFF_PP} punktów
 * procentowych i test dwóch proporcji daje |z| ≥ {@link #Z_CRITICAL} (ok. 95%). W pozostałych przypadkach
 * odpowiedź mówi wprost, dlaczego nie da się jeszcze nic orzec.</p>
 */
public final class EffectivenessCalculator {

    /** Grupa mniejsza niż tyle aplikacji to „mała próba”. */
    static final int MIN_SAMPLE = 5;
    /** Minimalna różnica odsetka odpowiedzi (pp), żeby w ogóle mówić o różnicy. */
    static final double MIN_DIFF_PP = 20.0;
    /** Próg statystyki z testu dwóch proporcji (≈ poziom ufności 95%). */
    static final double Z_CRITICAL = 1.96;

    static final String UNKNOWN_SOURCE = "nieznane źródło";
    static final String NO_CV = "bez wersji CV";

    private EffectivenessCalculator() {
    }

    /**
     * Statystyki jednej grupy (źródła lub wersji CV).
     *
     * @param name                 nazwa grupy
     * @param applications         wysłane aplikacje
     * @param responses            aplikacje z odpowiedzią firmy
     * @param responseRate         % odpowiedzi
     * @param interviews           aplikacje, w których doszło do rozmowy
     * @param interviewRate        % rozmów
     * @param medianDaysToResponse mediana dni do pierwszej odpowiedzi (null, gdy brak danych)
     * @param smallSample          czy próba jest mniejsza niż {@link #MIN_SAMPLE}
     */
    public record Group(String name, int applications, int responses, Double responseRate, int interviews,
                        Double interviewRate, Double medianDaysToResponse, boolean smallSample) {
    }

    /**
     * Wniosek dla jednego wymiaru porównania.
     *
     * @param conclusive czy różnica jest duża i próby wystarczające
     * @param text       zdanie dla kandydata
     */
    public record Insight(boolean conclusive, String text) {
    }

    public record Effectiveness(int applications, int minSample, List<Group> bySource, Insight sourceInsight,
                                List<Group> byCv, Insight cvInsight) {
    }

    public static Effectiveness compute(List<AppFacts> apps) {
        List<AppFacts> sent = apps.stream().filter(AppFacts::sent).toList();
        List<Group> bySource = groups(sent, a -> a.source() == null ? UNKNOWN_SOURCE : a.source());
        List<Group> byCv = groups(sent, a -> Text.isBlank(a.application().getCvVariantName()) ? NO_CV
                : a.application().getCvVariantName().strip());
        return new Effectiveness(sent.size(), MIN_SAMPLE, bySource, insight(bySource, "źródło", "źródłach"),
                byCv, insight(byCv, "wersja CV", "wersjach CV"));
    }

    static List<Group> groups(List<AppFacts> sent, Function<AppFacts, String> key) {
        // grupowanie bez rozróżniania wielkości liter i polskich znaków; nazwa — pierwsza napotkana
        Map<String, List<AppFacts>> buckets = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (AppFacts a : sent) {
            String name = key.apply(a);
            String k = Text.fold(name).strip();
            names.putIfAbsent(k, name);
            buckets.computeIfAbsent(k, x -> new ArrayList<>()).add(a);
        }
        List<Group> out = new ArrayList<>();
        buckets.forEach((k, list) -> out.add(group(names.get(k), list)));
        out.sort(Comparator.comparingInt(Group::applications).reversed().thenComparing(Group::name));
        return out;
    }

    static Group group(String name, List<AppFacts> list) {
        int n = list.size();
        int responses = (int) list.stream().filter(a -> a.reached(Stage.RESPONSE)).count();
        int interviews = (int) list.stream().filter(a -> a.reached(Stage.INTERVIEW)).count();
        List<Long> days = list.stream().map(AppFacts::daysToResponse).filter(d -> d != null).toList();
        return new Group(name, n, responses, Labels.percent(responses, n), interviews, Labels.percent(interviews, n),
                median(days), n < MIN_SAMPLE);
    }

    static Double median(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid).doubleValue();
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    /** Statystyka z testu dwóch proporcji (0, gdy nie da się jej policzyć). */
    static double twoProportionZ(int x1, int n1, int x2, int n2) {
        if (n1 == 0 || n2 == 0) {
            return 0;
        }
        double p1 = (double) x1 / n1;
        double p2 = (double) x2 / n2;
        double p = (double) (x1 + x2) / (n1 + n2);
        double se = Math.sqrt(p * (1 - p) * (1.0 / n1 + 1.0 / n2));
        return se == 0 ? 0 : (p1 - p2) / se;
    }

    static Insight insight(List<Group> groups, String dimension, String dimensionLocative) {
        List<Group> eligible = groups.stream().filter(g -> !g.smallSample()).toList();
        if (eligible.size() < 2) {
            return new Insight(false, "Za mało danych, by porównywać " + (dimension.equals("źródło") ? "źródła"
                    : "wersje CV") + ": potrzeba co najmniej dwóch grup po " + MIN_SAMPLE
                    + " wysłanych aplikacji. Na razie to tylko obserwacje.");
        }
        Group best = eligible.stream().max(Comparator.comparingDouble(EffectivenessCalculator::rate)).orElseThrow();
        Group worst = eligible.stream().min(Comparator.comparingDouble(EffectivenessCalculator::rate)).orElseThrow();
        double diff = rate(best) - rate(worst);
        String numbers = String.format(Locale.ROOT, "%s vs %s, próby: %d i %d", Labels.pct(best.responseRate()),
                Labels.pct(worst.responseRate()), best.applications(), worst.applications());
        if (diff < MIN_DIFF_PP) {
            return new Insight(false, "Odsetek odpowiedzi jest zbliżony we wszystkich " + dimensionLocative
                    + " z wystarczającą próbą (różnica " + Math.round(diff)
                    + " pp) — nie ma podstaw, by którąś preferować.");
        }
        double z = Math.abs(twoProportionZ(best.responses(), best.applications(), worst.responses(),
                worst.applications()));
        if (z < Z_CRITICAL) {
            return new Insight(false, "Różnica między „" + best.name() + "” a „" + worst.name() + "” (" + numbers
                    + ") może być przypadkowa przy tak małych próbach — zbierz więcej aplikacji, zanim zmienisz strategię.");
        }
        String subject = dimension.equals("źródło") ? "Źródło" : "Wersja CV";
        return new Insight(true, subject + " „" + best.name() + "” daje wyraźnie wyższy odsetek odpowiedzi niż „"
                + worst.name() + "” (" + numbers + "). To korelacja, a nie dowód przyczyny — na wynik wpływa też "
                + "rodzaj ofert, na które aplikujesz.");
    }

    private static double rate(Group g) {
        return g.responseRate() == null ? 0 : g.responseRate();
    }
}

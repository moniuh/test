package pl.radarpracy.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Trendy umiejętności w ofertach (pomysł #75): które umiejętności najczęściej pojawiają się w wymaganiach
 * zebranych ofert w bieżącym oknie czasu, czy kandydat je ma i jak zmienił się ich udział względem
 * poprzedniego okna o tej samej długości.
 *
 * <p>Reguły trendu: porównujemy tylko, gdy oba okna mają co najmniej {@link #TREND_MIN_OFFERS} ofert
 * (przy mniejszej liczbie jedna oferta zmienia udział o ponad 20 pp);
 * zmiana udziału o co najmniej {@link #TREND_THRESHOLD_PP} pp to wzrost/spadek, mniejsza — stabilnie.</p>
 */
public final class SkillTrendCalculator {

    static final int TREND_MIN_OFFERS = 5;
    static final double TREND_THRESHOLD_PP = 10.0;

    private SkillTrendCalculator() {
    }

    /** Umiejętności z wymagań jednej oferty (każda liczona raz; obowiązkowa ma pierwszeństwo). */
    public record OfferSkills(LocalDate date, Set<String> mustHave, Set<String> niceToHave) {
    }

    /**
     * @param skill           nazwa kanoniczna
     * @param category        kategoria ze słownika
     * @param offers          liczba ofert w bieżącym oknie, które jej wymagają
     * @param mustHave        w tym jako wymaganie obowiązkowe
     * @param niceToHave      w tym tylko jako mile widziane
     * @param share           udział ofert z bieżącego okna (%)
     * @param previousOffers  liczba ofert w poprzednim oknie
     * @param previousShare   udział w poprzednim oknie (%; null, gdy okno puste)
     * @param deltaPp         zmiana udziału w pp (null, gdy brak porównania)
     * @param trend           UP, DOWN, STABLE albo UNKNOWN (za mało danych)
     * @param candidateHas    czy kandydat ma umiejętność (CV lub profil)
     * @param candidateSource „CV” albo „profil” (null, gdy brak)
     */
    public record SkillTrend(String skill, String category, int offers, int mustHave, int niceToHave, Double share,
                             int previousOffers, Double previousShare, Double deltaPp, String trend,
                             boolean candidateHas, String candidateSource) {
    }

    public record SkillTrends(int days, LocalDate from, LocalDate to, int offersInWindow, LocalDate previousFrom,
                              LocalDate previousTo, int offersInPreviousWindow, boolean comparable,
                              int candidateHasCount, List<SkillTrend> skills, String summary) {
    }

    private static final class Counter {
        int must;
        int nice;
        int previous;

        int total() {
            return must + nice;
        }
    }

    public static SkillTrends compute(List<OfferSkills> offers, LocalDate today, int days, int limit,
                                      Map<String, String> candidateSkills, Function<String, String> categoryOf) {
        LocalDate from = today.minusDays(days - 1L);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = from.minusDays(days);
        Map<String, Counter> counts = new HashMap<>();
        int current = 0;
        int previous = 0;
        for (OfferSkills o : offers) {
            if (o.date() == null) {
                continue;
            }
            boolean inCurrent = !o.date().isBefore(from) && !o.date().isAfter(today);
            boolean inPrevious = !o.date().isBefore(prevFrom) && !o.date().isAfter(prevTo);
            if (!inCurrent && !inPrevious) {
                continue;
            }
            Set<String> all = new LinkedHashSet<>(o.mustHave());
            all.addAll(o.niceToHave());
            if (inCurrent) {
                current++;
                for (String s : all) {
                    Counter c = counts.computeIfAbsent(s, k -> new Counter());
                    if (o.mustHave().contains(s)) {
                        c.must++;
                    } else {
                        c.nice++;
                    }
                }
            } else {
                previous++;
                for (String s : all) {
                    counts.computeIfAbsent(s, k -> new Counter()).previous++;
                }
            }
        }
        boolean comparable = current >= TREND_MIN_OFFERS && previous >= TREND_MIN_OFFERS;
        List<SkillTrend> list = new ArrayList<>();
        for (Map.Entry<String, Counter> e : counts.entrySet()) {
            Counter c = e.getValue();
            if (c.total() == 0) {
                continue;
            }
            Double share = Labels.percent(c.total(), current);
            Double prevShare = Labels.percent(c.previous, previous);
            Double delta = null;
            String trend = "UNKNOWN";
            if (comparable) {
                delta = Math.round(10.0 * (share - prevShare)) / 10.0;
                trend = delta >= TREND_THRESHOLD_PP ? "UP" : delta <= -TREND_THRESHOLD_PP ? "DOWN" : "STABLE";
            }
            String source = candidateSkills.get(e.getKey());
            list.add(new SkillTrend(e.getKey(), categoryOf.apply(e.getKey()), c.total(), c.must, c.nice, share,
                    c.previous, prevShare, delta, trend, source != null, source));
        }
        list.sort(Comparator.comparingInt(SkillTrend::offers).reversed()
                .thenComparing(Comparator.comparingInt(SkillTrend::mustHave).reversed())
                .thenComparing(SkillTrend::skill));
        List<SkillTrend> top = list.stream().limit(Math.max(1, limit)).toList();
        int has = (int) top.stream().filter(SkillTrend::candidateHas).count();
        return new SkillTrends(days, from, today, current, prevFrom, prevTo, previous, comparable, has, top,
                summary(days, current, comparable, top, has));
    }

    private static String summary(int days, int current, boolean comparable, List<SkillTrend> top, int has) {
        if (current == 0) {
            return "Brak ofert z ostatnich " + days + " dni — dodaj oferty, aby zobaczyć trendy.";
        }
        StringBuilder sb = new StringBuilder("W ostatnich ").append(days).append(" dniach: ")
                .append(Labels.count(current, "oferta", "oferty", "ofert")).append(".");
        if (!top.isEmpty()) {
            sb.append(" Najczęściej wymagane: ");
            List<String> names = new ArrayList<>();
            for (SkillTrend t : top.stream().limit(3).toList()) {
                names.add(t.skill() + " (" + Labels.pct(t.share()) + ")");
            }
            sb.append(String.join(", ", names)).append(". Masz ").append(has).append(" z ").append(top.size())
                    .append(" najczęściej wymaganych umiejętności.");
            List<String> missingTop = top.stream().filter(t -> !t.candidateHas()).limit(3).map(SkillTrend::skill)
                    .toList();
            if (!missingTop.isEmpty()) {
                sb.append(" Najczęstsze braki: ").append(String.join(", ", missingTop)).append(".");
            }
        }
        if (!comparable) {
            sb.append(" Porównanie z poprzednim okresem wymaga co najmniej ").append(TREND_MIN_OFFERS)
                    .append(" ofert w obu okresach.");
        }
        return sb.toString();
    }
}

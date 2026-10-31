package pl.radarpracy.analytics;

import pl.radarpracy.analytics.AppFacts.Stage;
import pl.radarpracy.application.ApplicationStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lejek rekrutacyjny (pomysł #71): wysłane → odpowiedź firmy → rozmowa → oferta → przyjęta.
 *
 * <p>Do lejka trafiają tylko aplikacje wysłane (PLANNED się nie liczy), których data wysłania mieści się
 * w zakresie [from, to] (granice włącznie, null = bez ograniczenia). Konwersje liczy kod.</p>
 */
public final class FunnelCalculator {

    /** Minimalna liczebność etapu, od której wskazujemy przejście z największym odpływem. */
    static final int MIN_SENT_FOR_BOTTLENECK = 5;

    private FunnelCalculator() {
    }

    /**
     * Etap lejka.
     *
     * @param key                    identyfikator etapu (SENT, RESPONSE, INTERVIEW, OFFER, ACCEPTED)
     * @param label                  nazwa po polsku
     * @param count                  liczba aplikacji, które osiągnęły etap
     * @param conversionFromPrevious % z poprzedniego etapu (null dla pierwszego lub gdy poprzedni = 0)
     * @param conversionFromStart    % wszystkich wysłanych (null, gdy brak wysłanych)
     */
    public record FunnelStage(String key, String label, int count, Double conversionFromPrevious,
                              Double conversionFromStart) {
    }

    /**
     * Wynik lejka.
     *
     * @param from            początek zakresu (null = od początku)
     * @param to              koniec zakresu (null = bez ograniczenia)
     * @param plannedExcluded aplikacje w statusie „do wysłania” — pominięte w lejku
     * @param stages          etapy w kolejności
     * @param summary         podsumowanie słowne
     */
    public record Funnel(LocalDate from, LocalDate to, int plannedExcluded, List<FunnelStage> stages,
                         String summary) {
    }

    private static final Stage[] STAGES = {Stage.SENT, Stage.RESPONSE, Stage.INTERVIEW, Stage.OFFER, Stage.ACCEPTED};

    static String label(Stage s) {
        return switch (s) {
            case SENT -> "Wysłane";
            case RESPONSE -> "Odpowiedź firmy";
            case INTERVIEW -> "Rozmowa";
            case OFFER -> "Oferta";
            case ACCEPTED -> "Przyjęta";
            case NOT_SENT -> "Niewysłane";
        };
    }

    public static Funnel compute(List<AppFacts> apps, LocalDate from, LocalDate to) {
        List<AppFacts> inRange = apps.stream().filter(AppFacts::sent)
                .filter(a -> inRange(a.sentDate(), from, to)).toList();
        int planned = (int) apps.stream().filter(a -> a.status() == ApplicationStatus.PLANNED).count();

        List<FunnelStage> stages = new ArrayList<>();
        int start = inRange.size();
        int previous = -1;
        for (Stage s : STAGES) {
            int count = (int) inRange.stream().filter(a -> a.reached(s)).count();
            Double fromPrevious = previous < 0 ? null : Labels.percent(count, previous);
            stages.add(new FunnelStage(s.name(), label(s), count, fromPrevious, Labels.percent(count, start)));
            previous = count;
        }
        return new Funnel(from, to, planned, stages, summary(stages));
    }

    static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return from == null && to == null;
        }
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static String summary(List<FunnelStage> stages) {
        int sent = stages.get(0).count();
        if (sent == 0) {
            return "Brak wysłanych aplikacji w wybranym okresie.";
        }
        FunnelStage response = stages.get(1);
        FunnelStage interview = stages.get(2);
        StringBuilder sb = new StringBuilder();
        sb.append("Z ").append(Labels.count(sent, "wysłanej aplikacji", "wysłanych aplikacji", "wysłanych aplikacji"))
                .append(" odpowiedź przyszła w ").append(response.count()).append(" (")
                .append(Labels.pct(response.conversionFromStart())).append("), a do rozmowy doszło w ")
                .append(interview.count()).append(" (").append(Labels.pct(interview.conversionFromStart()))
                .append(").");
        if (sent >= MIN_SENT_FOR_BOTTLENECK) {
            FunnelStage worst = null;
            String worstFrom = null;
            for (int i = 1; i < stages.size(); i++) {
                FunnelStage s = stages.get(i);
                // tylko przejścia z wystarczająco liczebnego etapu — inaczej 0 z 1 wyglądałoby na „wąskie gardło”
                if (stages.get(i - 1).count() < MIN_SENT_FOR_BOTTLENECK || s.conversionFromPrevious() == null) {
                    continue;
                }
                if (worst == null || s.conversionFromPrevious() < worst.conversionFromPrevious()) {
                    worst = s;
                    worstFrom = stages.get(i - 1).label();
                }
            }
            if (worst != null && worst.conversionFromPrevious() < 100) {
                sb.append(" Największy odpływ: „").append(worstFrom).append("” → „").append(worst.label())
                        .append("” (przechodzi ").append(Labels.pct(worst.conversionFromPrevious())).append(").");
            }
        } else {
            sb.append(" Przy mniej niż ").append(MIN_SENT_FOR_BOTTLENECK)
                    .append(" aplikacjach konwersje łatwo się wahają — traktuj je orientacyjnie.");
        }
        return sb.toString();
    }
}

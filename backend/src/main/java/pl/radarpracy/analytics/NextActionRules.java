package pl.radarpracy.analytics;

import pl.radarpracy.analytics.AppFacts.Stage;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * „Co dalej?” (pomysł #97) — lista działań na dziś wyliczona z danych według jawnych reguł.
 *
 * <p>Priorytet (wyższy = ważniejszy) i progi są stałymi poniżej. Kolejność reguł:</p>
 * <ol>
 *   <li>{@link #P_MOOD} — ostrzeżenie z dziennika samopoczucia (#76): najpierw zadbaj o siebie.</li>
 *   <li>{@link #P_INTERVIEW_SOON} — rozmowa w ciągu {@link #INTERVIEW_SOON_HOURS} h → przygotuj się.</li>
 *   <li>{@link #P_FOLLOW_UP_OVERDUE} (+ do 9 za dni zwłoki) — follow-up z terminem ≤ dziś w otwartej aplikacji.</li>
 *   <li>{@link #P_DEBRIEF} — odbyta rozmowa (do {@link #DEBRIEF_MAX_AGE_DAYS} dni) bez debriefu.</li>
 *   <li>{@link #P_FOLLOW_UP_SILENCE} — wysłana aplikacja bez odpowiedzi i bez terminu follow-upu, cisza dłużej niż
 *       {@code followUpDays} z profilu.</li>
 *   <li>{@link #P_HIGH_MATCH} (+ do 5 za wynik) — oferta z dopasowaniem ≥ {@link #HIGH_MATCH_SCORE} bez aplikacji
 *       (najwyżej {@link #MAX_OFFER_ACTIONS}).</li>
 *   <li>{@link #P_PLANNED_STALE} — aplikacja „do wysłania” starsza niż {@link #PLANNED_STALE_DAYS} dni.</li>
 *   <li>{@link #P_WEEKLY_GOAL} — brakuje aplikacji do celu tygodniowego.</li>
 *   <li>{@link #P_SOFT_SKILL} — kompetencja miękka wymagana w co najmniej {@link #SKILL_GAP_MIN_OFFERS} ofertach,
 *       o której CV nie wspomina (#30) → pokaż ją w CV.</li>
 *   <li>{@link #P_SKILL_GAP} — umiejętność (nie miękka, nieobecna w CV) brakująca w co najmniej
 *       {@link #SKILL_GAP_MIN_OFFERS} ofertach (#30) → rozważ naukę.</li>
 * </ol>
 * Oferta z firmy, do której już aplikowano (np. to samo ogłoszenie z innego portalu), dostaje
 * {@link #SAME_COMPANY_PENALTY} pkt mniej i ostrzeżenie w uzasadnieniu.
 * Poziom: priorytet ≥ {@link #HIGH_FROM} = HIGH, ≥ {@link #MEDIUM_FROM} = MEDIUM, niżej LOW. Wyjątek: wpis
 * o samopoczuciu jest pierwszy na liście, ale ma poziom MEDIUM — nie chcemy dokładać presji etykietą „pilne”.
 */
public final class NextActionRules {

    static final int INTERVIEW_SOON_HOURS = 48;
    static final int HIGH_MATCH_SCORE = 75;
    static final int MAX_OFFER_ACTIONS = 3;
    static final int PLANNED_STALE_DAYS = 3;
    static final int DEBRIEF_MAX_AGE_DAYS = 30;
    static final int SKILL_GAP_MIN_OFFERS = 3;
    static final int DEFAULT_FOLLOW_UP_DAYS = 7;

    static final int P_MOOD = 100;
    static final int P_INTERVIEW_SOON = 96;
    static final int P_FOLLOW_UP_OVERDUE = 85;
    static final int P_DEBRIEF = 75;
    static final int P_FOLLOW_UP_SILENCE = 70;
    static final int P_HIGH_MATCH = 60;
    static final int P_PLANNED_STALE = 55;
    static final int P_WEEKLY_GOAL = 50;
    static final int P_SOFT_SKILL = 45;
    static final int P_SKILL_GAP = 40;
    static final int SAME_COMPANY_PENALTY = 5;

    static final int HIGH_FROM = 80;
    static final int MEDIUM_FROM = 55;

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM, 'godz.' HH:mm",
            Locale.forLanguageTag("pl-PL"));

    private NextActionRules() {
    }

    /**
     * Działanie do wykonania.
     *
     * @param priority liczba — wyższa = ważniejsze
     * @param level    HIGH / MEDIUM / LOW
     * @param kind     rodzaj reguły (np. FOLLOW_UP)
     * @param title    co zrobić
     * @param reason   dlaczego (z liczbami)
     * @param link     ścieżka w aplikacji (frontend), np. „/aplikacje/5”
     */
    public record NextAction(int priority, String level, String kind, String title, String reason, String link) {
    }

    /**
     * Oferta bez aplikacji z wynikiem dopasowania.
     *
     * @param sameCompanyApplicationId istniejąca aplikacja do tej samej firmy (null, gdy brak)
     */
    public record OfferCandidate(Long offerId, String title, String company, int score, Long sameCompanyApplicationId) {
    }

    /**
     * Wejście reguł.
     *
     * @param today           dzisiejsza data
     * @param now             bieżący moment
     * @param zone            strefa do zamiany znaczników czasu
     * @param applications    fakty o aplikacjach
     * @param unappliedOffers aktywne oferty bez aplikacji (z wynikiem)
     * @param week            postęp celu tygodniowego
     * @param skillGaps       luki kompetencyjne posortowane wg ważności (#30)
     * @param moodWarning     ostrzeżenie z dziennika (null, gdy brak)
     * @param followUpDays    po ilu dniach ciszy przypominać (z profilu)
     */
    public record Inputs(LocalDate today, LocalDateTime now, ZoneId zone, List<AppFacts> applications,
                         List<OfferCandidate> unappliedOffers, WeeklyGoal.Week week,
                         List<SkillGapCalculator.SkillGap> skillGaps, MoodRules.Warning moodWarning,
                         Integer followUpDays) {
    }

    public static List<NextAction> compute(Inputs in) {
        List<NextAction> out = new ArrayList<>();
        mood(in, out);
        interviews(in, out);
        followUps(in, out);
        highMatches(in, out);
        plannedStale(in, out);
        weeklyGoal(in, out);
        softSkill(in, out);
        skillGap(in, out);
        out.sort(Comparator.comparingInt(NextAction::priority).reversed().thenComparing(NextAction::title));
        return out;
    }

    static String level(int priority) {
        return priority >= HIGH_FROM ? "HIGH" : priority >= MEDIUM_FROM ? "MEDIUM" : "LOW";
    }

    private static void add(List<NextAction> out, int priority, String kind, String title, String reason, String link) {
        out.add(new NextAction(priority, level(priority), kind, title, reason, link));
    }

    private static void mood(Inputs in, List<NextAction> out) {
        if (in.moodWarning() == null) {
            return;
        }
        out.add(new NextAction(P_MOOD, "MEDIUM", "MOOD_CHECK", "Zadbaj o siebie — rozważ przerwę",
                String.join(" ", in.moodWarning().reasons()) + " Możesz tymczasowo obniżyć cel tygodniowy do "
                        + in.moodWarning().suggestedGoal() + ".", "/analityka"));
    }

    private static void interviews(Inputs in, List<NextAction> out) {
        LocalDateTime soon = in.now().plusHours(INTERVIEW_SOON_HOURS);
        LocalDateTime debriefLimit = in.now().minusDays(DEBRIEF_MAX_AGE_DAYS);
        for (AppFacts a : in.applications()) {
            for (Interview i : a.interviews()) {
                LocalDateTime at = i.getScheduledAt();
                if (at == null) {
                    continue;
                }
                int duration = i.getDurationMinutes() == null ? 60 : i.getDurationMinutes();
                LocalDateTime end = at.plusMinutes(duration);
                if (!a.status().isClosed() && !at.isBefore(in.now()) && !at.isAfter(soon)) {
                    long hours = ChronoUnit.HOURS.between(in.now(), at);
                    add(out, P_INTERVIEW_SOON + (hours < 24 ? 2 : 0), "INTERVIEW_PREP",
                            "Przygotuj się do rozmowy: " + a.company(),
                            "Rozmowa na stanowisko " + a.position() + " — " + WHEN.format(at) + " (rodzaj: "
                                    + Labels.interviewKind(i.getKind()) + "). Przejrzyj ofertę, dopasowanie i pytania.",
                            "/oferty/" + a.offerId());
                } else if (end.isBefore(in.now()) && !end.isBefore(debriefLimit) && i.getDebriefedAt() == null) {
                    add(out, P_DEBRIEF, "DEBRIEF", "Zapisz wnioski z rozmowy: " + a.company(),
                            "Rozmowa odbyła się " + WHEN.format(at) + " i nie ma jeszcze debriefu. Notatki zrobione "
                                    + "na świeżo pomagają przed kolejnymi rozmowami.", "/rozmowy");
                }
            }
        }
    }

    private static void followUps(Inputs in, List<NextAction> out) {
        int silenceDays = in.followUpDays() == null || in.followUpDays() < 1 ? DEFAULT_FOLLOW_UP_DAYS
                : in.followUpDays();
        for (AppFacts a : in.applications()) {
            if (a.status().isClosed()) {
                continue;
            }
            LocalDate due = a.application().getNextFollowUpAt();
            if (due != null) {
                if (!due.isAfter(in.today())) {
                    long overdue = ChronoUnit.DAYS.between(due, in.today());
                    add(out, P_FOLLOW_UP_OVERDUE + (int) Math.min(overdue, 9), "FOLLOW_UP",
                            "Wyślij follow-up: " + a.company(),
                            (overdue == 0 ? "Termin follow-upu mija dziś" : "Termin follow-upu minął "
                                    + Labels.count(overdue, "dzień", "dni", "dni") + " temu") + " (" + a.position()
                                    + ", status: " + Labels.status(a.status()).toLowerCase(Locale.ROOT) + ").",
                            "/aplikacje/" + a.id());
                }
                continue;
            }
            if (a.status() != ApplicationStatus.APPLIED || a.reached(Stage.RESPONSE) || a.sentDate() == null) {
                continue;
            }
            LocalDate lastActivity = lastActivity(a, in.zone());
            long silent = ChronoUnit.DAYS.between(lastActivity, in.today());
            if (silent >= silenceDays) {
                add(out, P_FOLLOW_UP_SILENCE, "FOLLOW_UP", "Przypomnij się: " + a.company(),
                        "Brak odpowiedzi od " + Labels.count(silent, "dnia", "dni", "dni") + " (próg z profilu: "
                                + silenceDays + "). Krótka, uprzejma wiadomość często przyspiesza decyzję.",
                        "/aplikacje/" + a.id());
            }
        }
    }

    /** Ostatnia aktywność kandydata w aplikacji: wysłanie, kontakt albo wysłany follow-up. */
    static LocalDate lastActivity(AppFacts a, ZoneId zone) {
        LocalDate last = a.sentDate();
        if (a.application().getLastContactAt() != null && a.application().getLastContactAt().isAfter(last)) {
            last = a.application().getLastContactAt();
        }
        for (ApplicationEvent e : a.events()) {
            if ((e.getType() == EventType.FOLLOW_UP_SENT || e.getType() == EventType.MESSAGE)
                    && e.getOccurredAt() != null) {
                LocalDate d = AppFacts.toDate(e.getOccurredAt(), zone);
                if (d.isAfter(last)) {
                    last = d;
                }
            }
        }
        return last;
    }

    private static void highMatches(Inputs in, List<NextAction> out) {
        in.unappliedOffers().stream()
                .filter(o -> o.score() >= HIGH_MATCH_SCORE)
                .sorted(Comparator.comparingInt(OfferCandidate::score).reversed())
                .limit(MAX_OFFER_ACTIONS)
                .forEach(o -> {
                    boolean sameCompany = o.sameCompanyApplicationId() != null;
                    int priority = P_HIGH_MATCH + Math.min(5, (o.score() - HIGH_MATCH_SCORE) / 5)
                            - (sameCompany ? SAME_COMPANY_PENALTY : 0);
                    String reason = "Dopasowanie do Twojego CV: " + o.score() + "% (próg reguły: " + HIGH_MATCH_SCORE
                            + "%), a nie ma jeszcze aplikacji.";
                    if (sameCompany) {
                        reason += " Uwaga: masz już aplikację do tej firmy — sprawdź, czy to nie to samo ogłoszenie "
                                + "z innego portalu.";
                    }
                    add(out, priority, "APPLY", "Rozważ aplikację: " + o.title()
                            + (o.company() == null ? "" : " — " + o.company()), reason, "/oferty/" + o.offerId());
                });
    }

    private static void plannedStale(Inputs in, List<NextAction> out) {
        for (AppFacts a : in.applications()) {
            if (a.status() != ApplicationStatus.PLANNED || a.plannedSince() == null) {
                continue;
            }
            long waiting = ChronoUnit.DAYS.between(a.plannedSince(), in.today());
            if (waiting > PLANNED_STALE_DAYS) {
                add(out, P_PLANNED_STALE, "SEND_PLANNED", "Wyślij zaplanowaną aplikację: " + a.company(),
                        "Czeka w „Do wysłania” od " + Labels.count(waiting, "dnia", "dni", "dni")
                                + " — oferty szybko się dezaktualizują. Wyślij ją albo usuń z planu.",
                        "/aplikacje/" + a.id());
            }
        }
    }

    private static void weeklyGoal(Inputs in, List<NextAction> out) {
        WeeklyGoal.Week w = in.week();
        if (w == null || w.remaining() <= 0) {
            return;
        }
        String reason = "W tym tygodniu wysłano " + w.applications() + " z " + w.goal() + " aplikacji (cel z profilu); "
                + "do niedzieli " + Labels.plural(w.daysLeft(), "został", "zostały", "zostało") + " "
                + Labels.count(w.daysLeft(), "dzień", "dni", "dni") + ".";
        if (in.moodWarning() != null) {
            reason += " Jeśli brakuje Ci sił, obniż cel w profilu — to w porządku.";
        }
        add(out, P_WEEKLY_GOAL, "WEEKLY_GOAL", "Do celu tygodniowego brakuje "
                + Labels.count(w.remaining(), "aplikacji", "aplikacji", "aplikacji"), reason, "/oferty");
    }

    private static void softSkill(Inputs in, List<NextAction> out) {
        SkillGapCalculator.SkillGap g = firstGap(in.skillGaps(), SkillGapCalculator.GapType.SOFT_SKILL);
        if (g == null) {
            return;
        }
        add(out, P_SOFT_SKILL, "SHOW_SOFT_SKILL", "Pokaż w CV: " + g.skill(),
                "Jest wymagana w " + Labels.count(g.offersMissing(), "ofercie", "ofertach", "ofertach")
                        + ", a Twoje CV o niej nie wspomina. Dopisz konkretny przykład z pracy — tylko jeśli to prawda.",
                "/profil");
    }

    private static void skillGap(Inputs in, List<NextAction> out) {
        SkillGapCalculator.SkillGap g = topGap(in.skillGaps());
        if (g == null) {
            return;
        }
        String reason = "Brakuje jej w " + Labels.count(g.offersMissing(), "ofercie", "ofertach", "ofertach")
                + " (w " + g.mustHaveMissing() + " jako wymaganie obowiązkowe)";
        reason += g.unlockedOffers() > 0
                ? "; po jej opanowaniu " + Labels.count(g.unlockedOffers(), "oferta przekroczyłaby", "oferty przekroczyłyby",
                "ofert przekroczyłoby") + " " + SkillGapCalculator.UNLOCK_THRESHOLD + "% dopasowania."
                : ".";
        add(out, P_SKILL_GAP, "LEARN_SKILL", "Rozważ naukę: " + g.skill(), reason, "/analityka");
    }

    /** Pierwsza luka „do nauczenia się” spełniająca próg liczby ofert (lista jest posortowana wg ważności). */
    static SkillGapCalculator.SkillGap topGap(List<SkillGapCalculator.SkillGap> gaps) {
        return firstGap(gaps, SkillGapCalculator.GapType.MISSING);
    }

    private static SkillGapCalculator.SkillGap firstGap(List<SkillGapCalculator.SkillGap> gaps,
                                                        SkillGapCalculator.GapType type) {
        if (gaps == null) {
            return null;
        }
        return gaps.stream().filter(g -> g.type() == type && g.offersMissing() >= SKILL_GAP_MIN_OFFERS).findFirst()
                .orElse(null);
    }
}

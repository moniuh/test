package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.NextActionRules.Inputs;
import pl.radarpracy.analytics.NextActionRules.NextAction;
import pl.radarpracy.analytics.NextActionRules.OfferCandidate;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.InterviewKind;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.analytics.Fixtures.app;

class NextActionRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 28); // środa
    static final LocalDateTime NOW = TODAY.atTime(9, 0);

    private static WeeklyGoal.Week week(int done, int goal) {
        return WeeklyGoal.compute(java.util.Collections.nCopies(done, TODAY), TODAY, goal, 1).current();
    }

    private static Inputs inputs(List<AppFacts> apps, List<OfferCandidate> offers, WeeklyGoal.Week week,
                                 SkillGapCalculator.SkillGap gap, MoodRules.Warning mood) {
        return new Inputs(TODAY, NOW, Fixtures.ZONE, apps, offers, week, gap == null ? List.of() : List.of(gap), mood,
                7);
    }

    private static SkillGapCalculator.SkillGap gap(String skill, int offers, int must, int unlocked,
                                                   SkillGapCalculator.GapType type) {
        return new SkillGapCalculator.SkillGap(skill, null, offers, must, 10, unlocked, List.of(), type, null);
    }

    @Test
    void everyRuleFiresAndListIsSortedByPriority() {
        AppFacts overdue = app(ApplicationStatus.APPLIED, TODAY.minusDays(12)).company("LogiTech")
                .followUp(TODAY.minusDays(5)).facts();
        AppFacts interviewSoon = app(ApplicationStatus.INTERVIEW, TODAY.minusDays(10)).company("Medikos")
                .interview(NOW.plusHours(30), InterviewKind.TECHNICAL, false).facts();
        AppFacts noDebrief = app(ApplicationStatus.INTERVIEW, TODAY.minusDays(20)).company("Kredyto")
                .interview(NOW.minusDays(2), InterviewKind.HR, false).facts();
        AppFacts planned = app(ApplicationStatus.PLANNED, null).company("BankPol")
                .event(EventType.CREATED, null, ApplicationStatus.PLANNED, TODAY.minusDays(5)).facts();
        AppFacts silent = app(ApplicationStatus.APPLIED, TODAY.minusDays(9)).company("Cisza sp. z o.o.").facts();
        SkillGapCalculator.SkillGap gap = gap("Kubernetes", 4, 3, 2, SkillGapCalculator.GapType.MISSING);
        MoodRules.Warning mood = new MoodRules.Warning(List.of("Średni nastrój z ostatnich 3 wpisów to 1,7 na 5."),
                "…", 4, "…");

        List<NextAction> actions = NextActionRules.compute(inputs(
                List.of(overdue, interviewSoon, noDebrief, planned, silent),
                List.of(new OfferCandidate(1L, "Senior Java", "FinPay", 88, null),
                        new OfferCandidate(2L, "Słaba", "X", 60, null)),
                week(2, 8), gap, mood));

        assertThat(actions).extracting(NextAction::kind).containsExactly(
                "MOOD_CHECK", "INTERVIEW_PREP", "FOLLOW_UP", "DEBRIEF", "FOLLOW_UP", "APPLY", "SEND_PLANNED",
                "WEEKLY_GOAL", "LEARN_SKILL");
        assertThat(actions).isSortedAccordingTo((a, b) -> Integer.compare(b.priority(), a.priority()));
        assertThat(actions.get(0).level()).isEqualTo("MEDIUM"); // samopoczucie: pierwsze, ale bez etykiety „pilne”
        assertThat(actions.get(1).level()).isEqualTo("HIGH");
        assertThat(actions.get(1).link()).isEqualTo("/oferty/" + interviewSoon.offerId());
        assertThat(actions.get(2).link()).isEqualTo("/aplikacje/" + overdue.id());
        assertThat(actions.get(2).reason()).contains("5 dni temu");
        assertThat(actions.get(3).link()).isEqualTo("/rozmowy");
        assertThat(actions.get(4).title()).contains("Cisza");
        assertThat(actions.get(4).reason()).contains("9 dni").contains("próg z profilu: 7");
        assertThat(actions.get(5).title()).contains("Senior Java");
        assertThat(actions.get(6).reason()).contains("5 dni");
        assertThat(actions.get(7).title()).isEqualTo("Do celu tygodniowego brakuje 6 aplikacji");
        assertThat(actions.get(7).reason()).contains("wysłano 2 z 8").contains("zostało 5 dni").contains("obniż cel");
        assertThat(actions.get(8).reason()).contains("4 ofertach").contains("2 oferty przekroczyłyby 70%");
        assertThat(actions.get(8).level()).isEqualTo("LOW");
    }

    @Test
    void quietDayProducesNoActions() {
        AppFacts closed = app(ApplicationStatus.REJECTED, TODAY.minusDays(30)).followUp(TODAY.minusDays(1))
                .interview(NOW.minusDays(20), InterviewKind.HR, true).facts();
        AppFacts fresh = app(ApplicationStatus.APPLIED, TODAY.minusDays(2)).facts();
        AppFacts plannedYesterday = app(ApplicationStatus.PLANNED, null)
                .event(EventType.CREATED, null, ApplicationStatus.PLANNED, TODAY.minusDays(1)).facts();
        List<NextAction> actions = NextActionRules.compute(inputs(List.of(closed, fresh, plannedYesterday),
                List.of(new OfferCandidate(3L, "Średnia", "Y", 74, null)), week(5, 5),
                gap("Go", 2, 2, 1, SkillGapCalculator.GapType.MISSING), null));
        assertThat(actions).isEmpty();
    }

    @Test
    void followUpEventResetsSilenceClock() {
        AppFacts a = app(ApplicationStatus.APPLIED, TODAY.minusDays(20))
                .event(EventType.FOLLOW_UP_SENT, null, null, TODAY.minusDays(3)).facts();
        assertThat(NextActionRules.lastActivity(a, Fixtures.ZONE)).isEqualTo(TODAY.minusDays(3));
        assertThat(NextActionRules.compute(inputs(List.of(a), List.of(), null, null, null))).isEmpty();
    }

    @Test
    void oldInterviewWithoutDebriefIsIgnoredAfterThirtyDays() {
        AppFacts a = app(ApplicationStatus.INTERVIEW, TODAY.minusDays(60))
                .interview(NOW.minusDays(40), InterviewKind.HR, false).facts();
        assertThat(NextActionRules.compute(inputs(List.of(a), List.of(), null, null, null))).isEmpty();
    }

    @Test
    void topGapRequiresEnoughOffersAndSkipsSoftSkillsAndLevelGaps() {
        var small = gap("A", 2, 2, 2, SkillGapCalculator.GapType.MISSING);
        var soft = gap("Komunikatywność", 6, 6, 0, SkillGapCalculator.GapType.SOFT_SKILL);
        var level = gap("Java", 6, 6, 0, SkillGapCalculator.GapType.LEVEL);
        var big = gap("B", 5, 1, 0, SkillGapCalculator.GapType.MISSING);
        assertThat(NextActionRules.topGap(List.of(small, soft, level, big))).isSameAs(big);
        assertThat(NextActionRules.topGap(List.of(small))).isNull();
    }

    @Test
    void softSkillIsSuggestedToShowInCvAndSameCompanyOfferIsFlagged() {
        var soft = gap("Komunikatywność", 3, 3, 0, SkillGapCalculator.GapType.SOFT_SKILL);
        List<NextAction> actions = NextActionRules.compute(new Inputs(TODAY, NOW, Fixtures.ZONE, List.of(),
                List.of(new OfferCandidate(4L, "Java Developer (Mid)", "LogiTech", 88, 1L),
                        new OfferCandidate(5L, "Inna", "Nordlys", 87, null)),
                null, List.of(soft), null, 7));
        assertThat(actions).extracting(NextAction::kind).containsExactly("APPLY", "APPLY", "SHOW_SOFT_SKILL");
        assertThat(actions.get(0).title()).contains("Inna");
        assertThat(actions.get(1).reason()).contains("masz już aplikację do tej firmy");
        assertThat(actions.get(2).title()).isEqualTo("Pokaż w CV: Komunikatywność");
        assertThat(actions.get(2).reason()).contains("w 3 ofertach").contains("tylko jeśli to prawda");
        assertThat(actions.get(2).link()).isEqualTo("/profil");
    }
}

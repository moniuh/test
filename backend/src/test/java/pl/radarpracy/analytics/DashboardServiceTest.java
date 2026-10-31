package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.DashboardService.FollowUp;
import pl.radarpracy.analytics.DashboardService.TopOffer;
import pl.radarpracy.analytics.DashboardService.UpcomingInterview;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.analytics.Fixtures.app;

class DashboardServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    static final LocalDateTime NOW = TODAY.atTime(12, 0);

    @Test
    void upcomingInterviewsWithinSevenDaysOnlyForOpenApplications() {
        AppFacts open = app(ApplicationStatus.INTERVIEW, TODAY.minusDays(10)).company("Medikos")
                .interview(NOW.plusDays(3), InterviewKind.TECHNICAL, false)
                .interview(NOW.plusDays(8), InterviewKind.FINAL, false)
                .interview(NOW.minusHours(1), InterviewKind.HR, false).facts();
        AppFacts closed = app(ApplicationStatus.REJECTED, TODAY.minusDays(10))
                .interview(NOW.plusDays(1), InterviewKind.HR, false).facts();
        List<UpcomingInterview> list = DashboardService.upcomingInterviews(List.of(open, closed), NOW);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).company()).isEqualTo("Medikos");
        assertThat(list.get(0).kindLabel()).isEqualTo("Techniczna");
    }

    @Test
    void overdueFollowUpsIncludeTodayAndSkipClosed() {
        AppFacts due = app(ApplicationStatus.APPLIED, TODAY.minusDays(12)).followUp(TODAY.minusDays(5)).facts();
        AppFacts today = app(ApplicationStatus.SCREENING, TODAY.minusDays(3)).followUp(TODAY).facts();
        AppFacts future = app(ApplicationStatus.APPLIED, TODAY.minusDays(1)).followUp(TODAY.plusDays(1)).facts();
        AppFacts closed = app(ApplicationStatus.NO_RESPONSE, TODAY.minusDays(40)).followUp(TODAY.minusDays(9)).facts();
        List<FollowUp> list = DashboardService.overdueFollowUps(List.of(future, today, closed, due), TODAY);
        assertThat(list).extracting(FollowUp::applicationId).containsExactly(due.id(), today.id());
        assertThat(list.get(0).daysOverdue()).isEqualTo(5);
        assertThat(list.get(1).daysOverdue()).isZero();
    }

    private static MatchResult result(JobOffer o, int score) {
        return new MatchResult(o.getId(), 1L, "CV", score, 0, 0, 0, 0, List.of(), List.of(), List.of("Go"), "ok",
                List.of());
    }

    @Test
    void topOffersAreActiveUnappliedAndSortedByScore() {
        JobOffer a = Fixtures.offer("A", "A");
        JobOffer b = Fixtures.offer("B", "B");
        JobOffer c = Fixtures.offer("C", "C");
        JobOffer rejected = Fixtures.offer("R", "R");
        rejected.setStatus(OfferStatus.REJECTED);
        JobOffer duplicate = Fixtures.offer("D", "D");
        duplicate.setDuplicateOfId(a.getId());
        b.setStatus(OfferStatus.SAVED);
        Map<Long, MatchResult> m = new HashMap<>();
        m.put(a.getId(), result(a, 60));
        m.put(b.getId(), result(b, 90));
        m.put(c.getId(), result(c, 95));
        m.put(rejected.getId(), result(rejected, 99));
        m.put(duplicate.getId(), result(duplicate, 98));
        Fixtures.App appliedToC = app(ApplicationStatus.APPLIED, TODAY);
        appliedToC.a.setOffer(c);

        List<TopOffer> top = DashboardService.topOffers(List.of(a, b, c, rejected, duplicate), m,
                List.of(appliedToC.facts()), 3, 0);
        assertThat(top).extracting(TopOffer::title).containsExactly("B", "A");
        assertThat(top).allMatch(t -> t.sameCompanyApplicationId() == null);
        assertThat(DashboardService.topOffers(List.of(a, b), m, List.of(), 3, 75)).extracting(TopOffer::title)
                .containsExactly("B");
    }

    @Test
    void offerFromCompanyWithExistingApplicationIsFlagged() {
        JobOffer original = Fixtures.offer("Java Developer", "LogiTech Solutions");
        JobOffer repost = Fixtures.offer("Java Developer (Mid)", "LogiTech Solutions sp. z o.o.");
        Fixtures.App applied = app(ApplicationStatus.APPLIED, TODAY);
        applied.a.setOffer(original);
        Map<Long, MatchResult> m = Map.of(original.getId(), result(original, 80), repost.getId(), result(repost, 88));
        List<TopOffer> top = DashboardService.topOffers(List.of(original, repost), m, List.of(applied.facts()), 3, 0);
        assertThat(top).hasSize(1);
        assertThat(top.get(0).sameCompanyApplicationId()).isEqualTo(applied.a.getId());
    }
}

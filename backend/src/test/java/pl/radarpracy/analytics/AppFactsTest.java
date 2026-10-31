package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.AppFacts.Stage;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.InterviewKind;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.analytics.Fixtures.app;

class AppFactsTest {

    static final LocalDate D = LocalDate.of(2026, 10, 1);

    @Test
    void plannedIsNeverSent() {
        AppFacts f = app(ApplicationStatus.PLANNED, null).facts();
        assertThat(f.sent()).isFalse();
        assertThat(f.stage()).isEqualTo(Stage.NOT_SENT);
    }

    @Test
    void withdrawnBeforeSendingIsNotSentButWithdrawnAfterApplyingIs() {
        AppFacts early = app(ApplicationStatus.WITHDRAWN, null)
                .event(EventType.CREATED, null, ApplicationStatus.PLANNED, D)
                .event(EventType.STATUS_CHANGED, ApplicationStatus.PLANNED, ApplicationStatus.WITHDRAWN, D.plusDays(1))
                .facts();
        assertThat(early.sent()).isFalse();

        AppFacts late = app(ApplicationStatus.WITHDRAWN, null)
                .event(EventType.CREATED, null, ApplicationStatus.APPLIED, D)
                .event(EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN, D.plusDays(3))
                .facts();
        assertThat(late.sent()).isTrue();
        assertThat(late.sentDate()).isEqualTo(D);
    }

    @Test
    void interviewRecordImpliesResponseStageEvenWithoutStatus() {
        AppFacts f = app(ApplicationStatus.APPLIED, D)
                .interview(D.plusDays(5).atTime(10, 0), InterviewKind.HR, false)
                .facts();
        assertThat(f.stage()).isEqualTo(Stage.INTERVIEW);
        assertThat(f.reached(Stage.RESPONSE)).isTrue();
        assertThat(f.reached(Stage.OFFER)).isFalse();
    }

    @Test
    void historyCountsLaterStagesAfterRejection() {
        // odrzucenie po rozmowie: status REJECTED, ale w historii był INTERVIEW
        AppFacts f = app(ApplicationStatus.REJECTED, D)
                .event(EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, D.plusDays(4))
                .event(EventType.STATUS_CHANGED, ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED, D.plusDays(9))
                .facts();
        assertThat(f.stage()).isEqualTo(Stage.INTERVIEW);
        assertThat(f.responseDate()).isEqualTo(D.plusDays(4));
        assertThat(f.daysToResponse()).isEqualTo(4);
    }

    @Test
    void firstResponseAtWinsOverEvents() {
        AppFacts f = app(ApplicationStatus.SCREENING, D).firstResponse(D.plusDays(2))
                .event(EventType.STATUS_CHANGED, ApplicationStatus.APPLIED, ApplicationStatus.SCREENING, D.plusDays(6))
                .facts();
        assertThat(f.stage()).isEqualTo(Stage.RESPONSE);
        assertThat(f.daysToResponse()).isEqualTo(2);
    }

    @Test
    void acceptedImpliesAllEarlierStages() {
        AppFacts f = app(ApplicationStatus.ACCEPTED, D).facts();
        assertThat(f.stage()).isEqualTo(Stage.ACCEPTED);
        assertThat(f.reached(Stage.SENT)).isTrue();
        assertThat(f.reached(Stage.INTERVIEW)).isTrue();
    }

    @Test
    void noResponseIsSentWithoutResponse() {
        AppFacts f = app(ApplicationStatus.NO_RESPONSE, D).facts();
        assertThat(f.stage()).isEqualTo(Stage.SENT);
        assertThat(f.daysToResponse()).isNull();
    }

    @Test
    void companyNamesAreNormalizedForComparison() {
        assertThat(AppFacts.normalizeCompany("LogiTech Solutions sp. z o.o."))
                .isEqualTo(AppFacts.normalizeCompany("LogiTech Solutions"))
                .isEqualTo("logitech solutions");
        assertThat(AppFacts.normalizeCompany("Nordlys Software AB")).isEqualTo("nordlys software");
        assertThat(AppFacts.normalizeCompany("Bank Śląski S.A.")).isEqualTo("bank slaski");
        assertThat(AppFacts.normalizeCompany(null)).isEmpty();
    }

    @Test
    void sourceFallsBackToOfferSource() {
        Fixtures.App a = app(ApplicationStatus.APPLIED, D);
        a.a.getOffer().setSource("justjoin.it");
        assertThat(a.facts().source()).isEqualTo("justjoin.it");
        assertThat(a.source("  polecenie ").facts().source()).isEqualTo("polecenie");
    }
}

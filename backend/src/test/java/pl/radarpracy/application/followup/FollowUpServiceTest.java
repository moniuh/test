package pl.radarpracy.application.followup;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.offer.JobOffer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.application;
import static pl.radarpracy.application.TestData.offer;

class FollowUpServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private static JobApplication app(long id, ApplicationStatus status, LocalDate next) {
        JobOffer o = offer(100 + id, "Java Developer " + id, "Firma " + id);
        JobApplication a = application(id, o, status, TODAY.minusDays(10));
        a.setNextFollowUpAt(next);
        return a;
    }

    @Test
    void splitsOverdueAndUpcomingWithinHorizon() {
        List<JobApplication> all = List.of(
                app(1, ApplicationStatus.APPLIED, TODAY.minusDays(5)),
                app(2, ApplicationStatus.APPLIED, TODAY),
                app(3, ApplicationStatus.APPLIED, TODAY.plusDays(3)),
                app(4, ApplicationStatus.APPLIED, TODAY.plusDays(30)),
                app(5, ApplicationStatus.APPLIED, null),
                app(6, ApplicationStatus.REJECTED, TODAY.minusDays(1)),
                app(7, ApplicationStatus.SCREENING, TODAY.minusDays(2)));

        FollowUpList list = FollowUpService.list(all, TODAY, 14, 7);

        assertThat(list.overdue()).extracting(FollowUpItem::applicationId).containsExactly(1L, 7L, 2L);
        assertThat(list.overdue().get(0).days()).isEqualTo(-5);
        assertThat(list.overdue().get(0).overdue()).isTrue();
        assertThat(list.overdue().get(2).days()).isZero();
        assertThat(list.upcoming()).extracting(FollowUpItem::applicationId).containsExactly(3L);
        assertThat(list.upcoming().get(0).days()).isEqualTo(3);
        assertThat(list.upcoming().get(0).overdue()).isFalse();
        assertThat(list.followUpDays()).isEqualTo(7);
        assertThat(list.today()).isEqualTo(TODAY);
    }

    @Test
    void itemCarriesOfferData() {
        FollowUpList list = FollowUpService.list(List.of(app(1, ApplicationStatus.APPLIED, TODAY)), TODAY, 14, 7);
        FollowUpItem item = list.overdue().get(0);
        assertThat(item.offerTitle()).isEqualTo("Java Developer 1");
        assertThat(item.company()).isEqualTo("Firma 1");
        assertThat(item.offerId()).isEqualTo(101L);
    }
}

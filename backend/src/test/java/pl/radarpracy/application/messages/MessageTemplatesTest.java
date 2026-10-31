package pl.radarpracy.application.messages;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.profile.CandidateProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.application;
import static pl.radarpracy.application.TestData.interview;
import static pl.radarpracy.application.TestData.offer;

class MessageTemplatesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);

    @Test
    void catalogCoversRequiredTemplatesWithKnownPlaceholders() {
        assertThat(MessageTemplates.ALL).extracting(MessageTemplate::id).containsExactly(
                "follow-up", "thank-you", "feedback-request", "decline-offer", "accept-offer", "reschedule");
        for (MessageTemplate t : MessageTemplates.ALL) {
            assertThat(MessageTemplates.PLACEHOLDERS.keySet()).containsAll(MessageTemplates.placeholdersIn(t));
            assertThat(t.body()).contains("{imie_nazwisko}");
            // neutralność płciowa: bez form zależnych od płci
            assertThat(t.body()).doesNotContain("wysłałam", "wysłałem", "byłam", "byłem", "chciałabym",
                    "chciałbym", "zainteresowana", "zainteresowany", "wdzięczna", "wdzięczny", "aplikowałam",
                    "aplikowałem");
        }
    }

    @Test
    void fillMarksMissingValues() {
        MessageTemplates.Filled f = MessageTemplates.fill("{firma} / {stanowisko} / {nieznany}",
                Map.of("firma", "Medikos", "stanowisko", " "));
        assertThat(f.text()).isEqualTo("Medikos / [stanowisko] / [nieznany]");
        assertThat(f.missing()).containsExactly("stanowisko", "nieznany");
    }

    @Test
    void rendersFollowUpWithApplicationAndProfileData() {
        JobApplication a = application(1, offer(2, "Java Developer", "LogiTech Solutions"), ApplicationStatus.APPLIED,
                LocalDate.of(2026, 10, 19));
        a.setContactName("Katarzyna Wiśniewska (Talent Acquisition)");
        a.setContactEmail("rekrutacja@logitech.example");
        CandidateProfile p = new CandidateProfile();
        p.setFullName("Anna Nowak");

        RenderedMessage m = MessageService.render(MessageTemplates.find("follow-up").orElseThrow(), a, p, List.of(), NOW);

        assertThat(m.to()).isEqualTo("Katarzyna Wiśniewska <rekrutacja@logitech.example>");
        assertThat(m.subject()).isEqualTo("Aplikacja na stanowisko Java Developer — pytanie o dalsze kroki");
        assertThat(m.body()).contains("dnia 19 października 2026").contains("w firmie LogiTech Solutions")
                .endsWith("Anna Nowak");
        assertThat(m.missing()).isEmpty();
        assertThat(m.text()).startsWith("Do: Katarzyna Wiśniewska <rekrutacja@logitech.example>\nTemat: ");
    }

    @Test
    void interviewDateComesFromPastOrUpcomingInterview() {
        JobApplication a = application(1, offer(2, "Fullstack Developer", "Medikos"), ApplicationStatus.INTERVIEW,
                LocalDate.of(2026, 10, 1));
        Interview past = interview(1, 1, LocalDateTime.of(2026, 10, 21, 10, 0));
        Interview next = interview(2, 1, LocalDateTime.of(2026, 11, 3, 14, 0));
        Interview later = interview(3, 1, LocalDateTime.of(2026, 11, 10, 9, 30));
        List<Interview> all = List.of(later, next, past);

        RenderedMessage thanks = MessageService.render(MessageTemplates.find("thank-you").orElseThrow(), a,
                new CandidateProfile(), all, NOW);
        assertThat(thanks.body()).contains("21 października 2026, godz. 10:00");
        assertThat(thanks.missing()).contains("imie_nazwisko", "kontakt");
        assertThat(thanks.to()).isNull();

        RenderedMessage resched = MessageService.render(MessageTemplates.find("reschedule").orElseThrow(), a,
                new CandidateProfile(), all, NOW);
        assertThat(resched.body()).contains("3 listopada 2026, godz. 14:00");

        RenderedMessage none = MessageService.render(MessageTemplates.find("reschedule").orElseThrow(), a,
                new CandidateProfile(), List.of(past), NOW);
        assertThat(none.body()).contains("[termin rozmowy]");
        assertThat(none.missing()).contains("data_rozmowy");
    }

    @Test
    void recipientFormatting() {
        assertThat(MessageService.recipient("Piotr Zieliński (Engineering Manager)", null)).isEqualTo("Piotr Zieliński");
        assertThat(MessageService.recipient(null, " hr@x.pl ")).isEqualTo("hr@x.pl");
        assertThat(MessageService.recipient(" ", null)).isNull();
    }
}

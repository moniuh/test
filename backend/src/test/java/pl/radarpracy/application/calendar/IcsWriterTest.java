package pl.radarpracy.application.calendar;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewClock;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.offer.JobOffer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.application;
import static pl.radarpracy.application.TestData.interview;
import static pl.radarpracy.application.TestData.offer;

class IcsWriterTest {

    private static final Instant STAMP = Instant.parse("2026-10-31T12:00:00Z");

    @Test
    void escapesTextValues() {
        assertThat(IcsWriter.escape("a;b,c\\d\nnowa\r\nlinia")).isEqualTo("a\\;b\\,c\\\\d\\nnowa\\nlinia");
        assertThat(IcsWriter.escape(null)).isEmpty();
    }

    @Test
    void foldsLongLinesAt75OctetsWithoutSplittingUtf8() {
        String value = "SUMMARY:" + "Rozmowa źdźbło ".repeat(20);
        String folded = IcsWriter.fold(value);
        String[] lines = folded.split("\r\n");
        assertThat(lines.length).isGreaterThan(1);
        for (int i = 0; i < lines.length; i++) {
            assertThat(lines[i].getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
            if (i > 0) {
                assertThat(lines[i]).startsWith(" ");
            }
        }
        // rozwinięcie (usunięcie CRLF + spacja) przywraca oryginał
        assertThat(folded.replace("\r\n ", "")).isEqualTo(value);
    }

    @Test
    void calendarHasRequiredPropertiesAndCrlf() {
        IcsWriter.Event e = new IcsWriter.Event("interview-1@radarpracy", Instant.parse("2026-11-03T13:00:00Z"),
                Instant.parse("2026-11-03T14:30:00Z"), "Rozmowa: Java, Kraków", "Biuro; piętro 3",
                "Linia 1\nLinia 2", "https://example.com/oferta", 60);
        String ics = IcsWriter.calendar("Radar", List.of(e), STAMP);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
        assertThat(ics).contains("VERSION:2.0\r\n", "PRODID:", "BEGIN:VEVENT\r\n", "END:VEVENT\r\n",
                "UID:interview-1@radarpracy\r\n", "DTSTAMP:20261031T120000Z\r\n", "DTSTART:20261103T130000Z\r\n",
                "DTEND:20261103T143000Z\r\n", "SUMMARY:Rozmowa: Java\\, Kraków\r\n",
                "LOCATION:Biuro\\; piętro 3\r\n", "DESCRIPTION:Linia 1\\nLinia 2\r\n",
                "URL:https://example.com/oferta\r\n", "TRIGGER:-PT60M\r\n");
    }

    @Test
    void interviewEventUsesConfiguredZoneAndOfferData() {
        InterviewClock clock = new InterviewClock(ZoneId.of("Europe/Warsaw"), Clock.fixed(STAMP, ZoneOffset.UTC));
        JobOffer o = offer(7, "Java Developer", "Medikos");
        o.setUrl("https://example.com/7");
        JobApplication a = application(3, o, ApplicationStatus.INTERVIEW, LocalDate.of(2026, 10, 1));
        a.setContactName("Katarzyna Wiśniewska");
        a.setContactEmail("rekrutacja@medikos.example");
        Interview i = interview(12, 3, LocalDateTime.of(2026, 11, 3, 14, 0));
        i.setKind(InterviewKind.TECHNICAL);
        i.setDurationMinutes(90);
        i.setLocation("https://meet.example.com/abc");
        i.setInterviewers("Tech Lead");

        IcsWriter.Event e = CalendarService.event(i, a, clock);

        // 14:00 w Warszawie w listopadzie (CET, UTC+1) = 13:00 UTC
        assertThat(e.start()).isEqualTo(Instant.parse("2026-11-03T13:00:00Z"));
        assertThat(e.end()).isEqualTo(Instant.parse("2026-11-03T14:30:00Z"));
        assertThat(e.uid()).isEqualTo("interview-12@radarpracy");
        assertThat(e.summary()).isEqualTo("Rozmowa (Techniczna): Java Developer — Medikos");
        assertThat(e.description()).contains("Prowadzący: Tech Lead")
                .contains("Kontakt: Katarzyna Wiśniewska <rekrutacja@medikos.example>")
                .contains("Ogłoszenie: https://example.com/7");
        assertThat(e.location()).isEqualTo("https://meet.example.com/abc");
    }
}

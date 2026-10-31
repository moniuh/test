package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import pl.radarpracy.analytics.ReportService.Report;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.common.BadRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.radarpracy.analytics.Fixtures.app;

class ReportTest {

    static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    static final LocalDate TO = LocalDate.of(2026, 10, 31);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);

    private static Report sample() {
        List<AppFacts> apps = List.of(
                app(ApplicationStatus.APPLIED, LocalDate.of(2026, 10, 5)).company("Firma; z „cudzysłowem”")
                        .source("pracuj.pl").facts(),
                app(ApplicationStatus.INTERVIEW, LocalDate.of(2026, 10, 2)).company("=HYPERLINK(\"x\")")
                        .source("LinkedIn")
                        .interview(LocalDateTime.of(2026, 10, 12, 14, 30), InterviewKind.TECHNICAL, true)
                        .event(EventType.FOLLOW_UP_SENT, null, null, LocalDate.of(2026, 10, 9)).facts(),
                app(ApplicationStatus.APPLIED, LocalDate.of(2026, 9, 20)).company("Za wcześnie").facts(),
                app(ApplicationStatus.PLANNED, null).company("Plan").facts());
        return ReportService.build(apps, "Anna Nowak", FROM, TO, NOW, Fixtures.ZONE);
    }

    @Test
    void reportContainsOnlyRangeAndSummary() {
        Report r = sample();
        assertThat(r.applications()).hasSize(2);
        assertThat(r.applications().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 2)); // sortowane wg daty
        assertThat(r.applications().get(0).statusLabel()).isEqualTo("Rozmowy");
        assertThat(r.interviews()).hasSize(1);
        assertThat(r.interviews().get(0).kindLabel()).isEqualTo("Techniczna");
        assertThat(r.summary().applicationsSent()).isEqualTo(2);
        assertThat(r.summary().distinctCompanies()).isEqualTo(2);
        assertThat(r.summary().responses()).isEqualTo(1);
        assertThat(r.summary().interviews()).isEqualTo(1);
        assertThat(r.summary().followUpsSent()).isEqualTo(1);
    }

    @Test
    void csvHasBomSemicolonsPolishHeadersAndSafeCells() {
        String csv = ReportCsv.write(sample());
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("Raport aktywności poszukiwania pracy;Anna Nowak\r\n");
        assertThat(csv).contains("Zakres dat;01.10.2026 – 31.10.2026");
        assertThat(csv).contains("Data;Firma;Stanowisko;Źródło;Status\r\n");
        assertThat(csv).contains("Data;Firma;Stanowisko;Rodzaj\r\n");
        assertThat(csv).contains("12.10.2026 14:30;");
        assertThat(csv).contains("\"Firma; z „cudzysłowem”\"");
        // ochrona przed formułą + podwojone cudzysłowy
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(csv).contains("Wysłane aplikacje;2\r\n");
        assertThat(csv).doesNotContain("Za wcześnie").doesNotContain("Plan;");
    }

    @Test
    void cellEscaping() {
        assertThat(ReportCsv.cell(null)).isEmpty();
        assertThat(ReportCsv.cell("zwykły")).isEqualTo("zwykły");
        assertThat(ReportCsv.cell("-5")).isEqualTo("'-5");
        assertThat(ReportCsv.cell("a\nb")).isEqualTo("\"a\nb\"");
    }

    @Test
    void rangeDefaultsAndValidation() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        LocalDate[] r = ReportService.range(null, null, today);
        assertThat(r[0]).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(r[1]).isEqualTo(today);
        assertThatThrownBy(() -> ReportService.range(today, today.minusDays(1), today))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ReportService.range(today.minusYears(3), today, today))
                .isInstanceOf(BadRequestException.class);
    }
}

package pl.radarpracy.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.analytics.DashboardService.Dashboard;
import pl.radarpracy.analytics.EffectivenessCalculator.Effectiveness;
import pl.radarpracy.analytics.FunnelCalculator.Funnel;
import pl.radarpracy.analytics.NextActionRules.NextAction;
import pl.radarpracy.analytics.ReportService.Report;
import pl.radarpracy.analytics.SkillGapService.SkillGapReport;
import pl.radarpracy.analytics.SkillTrendCalculator.SkillTrends;
import pl.radarpracy.common.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Analityka i motywacja: lejek, skuteczność, pulpit, „Co dalej?”, luki, trendy, raport. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsData data;
    private final DashboardService dashboard;
    private final NextActionService nextActions;
    private final SkillGapService skillGaps;
    private final SkillTrendService skillTrends;
    private final ReportService reports;

    public AnalyticsController(AnalyticsData data, DashboardService dashboard, NextActionService nextActions,
                               SkillGapService skillGaps, SkillTrendService skillTrends, ReportService reports) {
        this.data = data;
        this.dashboard = dashboard;
        this.nextActions = nextActions;
        this.skillGaps = skillGaps;
        this.skillTrends = skillTrends;
        this.reports = reports;
    }

    /** #71 Lejek rekrutacyjny; daty opcjonalne (ISO, granice włącznie). */
    @GetMapping("/funnel")
    Funnel funnel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Data początkowa nie może być późniejsza niż końcowa");
        }
        return FunnelCalculator.compute(data.applications(), from, to);
    }

    /** #72 Skuteczność wg źródła i wersji CV. */
    @GetMapping("/effectiveness")
    Effectiveness effectiveness() {
        return EffectivenessCalculator.compute(data.applications());
    }

    /** #73 Pulpit tygodniowy. */
    @GetMapping("/dashboard")
    Dashboard dashboard() {
        return dashboard.build(LocalDate.now(), LocalDateTime.now());
    }

    /** #97 „Co dalej?” — działania posortowane wg priorytetu. */
    @GetMapping("/next-actions")
    List<NextAction> nextActions() {
        return nextActions.compute(LocalDate.now(), LocalDateTime.now());
    }

    /** #30 Luki kompetencyjne na tle rynku (dla wskazanej lub aktywnej wersji CV). */
    @GetMapping("/skill-gaps")
    SkillGapReport skillGaps(@RequestParam(required = false) Long cvId) {
        return skillGaps.compute(cvId);
    }

    /** #75 Trendy umiejętności w ofertach. */
    @GetMapping("/skill-trends")
    SkillTrends skillTrends(@RequestParam(required = false) Integer days,
                            @RequestParam(required = false) Integer limit) {
        return skillTrends.compute(days, limit, LocalDate.now());
    }

    /** #79 Raport aktywności (JSON). Domyślnie ostatnie 30 dni. */
    @GetMapping("/report")
    Report report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.build(from, to, LocalDate.now(), LocalDateTime.now());
    }

    /** #79 Raport aktywności jako CSV (UTF-8 z BOM, separator „;”). */
    @GetMapping("/report.csv")
    ResponseEntity<byte[]> reportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Report r = reports.build(from, to, LocalDate.now(), LocalDateTime.now());
        byte[] body = ReportCsv.write(r).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ReportCsv.fileName(r)).build().toString())
                .body(body);
    }
}

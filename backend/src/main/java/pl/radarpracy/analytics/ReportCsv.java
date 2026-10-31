package pl.radarpracy.analytics;

import pl.radarpracy.analytics.ReportService.ApplicationRow;
import pl.radarpracy.analytics.ReportService.InterviewRow;
import pl.radarpracy.analytics.ReportService.Report;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Raport aktywności jako CSV (pomysł #79): UTF-8 z BOM (Excel rozpozna polskie znaki), separator „;”
 * (domyślny w polskich ustawieniach regionalnych), nagłówki po polsku, końce linii CRLF.
 *
 * <p>Pola zaczynające się od =, +, -, @ (oraz tabulatora i CR) poprzedzamy apostrofem — ochrona przed
 * wstrzyknięciem formuł do arkusza (treści ofert pochodzą z zewnątrz).</p>
 */
public final class ReportCsv {

    static final String BOM = "﻿";
    static final char SEP = ';';
    static final String EOL = "\r\n";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private ReportCsv() {
    }

    public static String write(Report r) {
        StringBuilder sb = new StringBuilder(BOM);
        row(sb, "Raport aktywności poszukiwania pracy", r.candidateName() == null ? "" : r.candidateName());
        row(sb, "Zakres dat", DATE.format(r.from()) + " – " + DATE.format(r.to()));
        row(sb, "Wygenerowano", DATE_TIME.format(r.generatedAt()));
        sb.append(EOL);

        row(sb, "Aplikacje");
        row(sb, "Data", "Firma", "Stanowisko", "Źródło", "Status");
        for (ApplicationRow a : r.applications()) {
            row(sb, a.date() == null ? "" : DATE.format(a.date()), a.company(), a.position(),
                    a.source() == null ? "" : a.source(), a.statusLabel());
        }
        sb.append(EOL);

        row(sb, "Rozmowy");
        row(sb, "Data", "Firma", "Stanowisko", "Rodzaj");
        for (InterviewRow i : r.interviews()) {
            row(sb, DATE_TIME.format(i.date()), i.company(), i.position(), i.kindLabel());
        }
        sb.append(EOL);

        ReportService.Summary s = r.summary();
        row(sb, "Podsumowanie");
        row(sb, "Wskaźnik", "Wartość");
        row(sb, "Wysłane aplikacje", String.valueOf(s.applicationsSent()));
        row(sb, "Liczba firm", String.valueOf(s.distinctCompanies()));
        row(sb, "Odpowiedzi firm", String.valueOf(s.responses()));
        row(sb, "Rozmowy", String.valueOf(s.interviews()));
        row(sb, "Otrzymane oferty", String.valueOf(s.offers()));
        row(sb, "Wysłane follow-upy", String.valueOf(s.followUpsSent()));
        return sb.toString();
    }

    private static void row(StringBuilder sb, String... cells) {
        sb.append(String.join(String.valueOf(SEP), List.of(cells).stream().map(ReportCsv::cell).toList()));
        sb.append(EOL);
    }

    /** Pojedyncza komórka: ochrona przed formułami, cudzysłowy przy separatorze/cudzysłowie/nowej linii. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(SEP) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Nazwa pliku do pobrania, np. raport-aktywnosci-2026-10-01_2026-10-31.csv. */
    static String fileName(Report r) {
        return "raport-aktywnosci-" + r.from() + "_" + r.to() + ".csv";
    }
}

package pl.radarpracy.tailoring;

import java.util.List;

/**
 * Raport strażnika prawdomówności (pomysł #68).
 *
 * @param passed            true, gdy brak naruszeń
 * @param violations        naruszenia z numerem linii dopasowanego CV
 * @param checkedLines      liczba sprawdzonych (niepustych) linii
 * @param verbatimLines     linie przepisane dosłownie z CV bazowego
 * @param rewrittenLines    linie zmienione, ale potwierdzone (przestawione pozycje, przeredagowane punkty)
 * @param generatedLines    linie wygenerowane z faktów (podsumowanie, kluczowe kompetencje)
 * @param profileOnlySkills umiejętności użyte w wersji dopasowanej, które są tylko w profilu (nie w CV bazowym)
 *                          — prawdziwe, ale warto umieć je potwierdzić na rozmowie
 */
public record GuardReport(boolean passed, List<Violation> violations, int checkedLines, int verbatimLines,
                          int rewrittenLines, int generatedLines, List<String> profileOnlySkills) {

    /** Rodzaj naruszenia. */
    public enum Kind {
        /** umiejętność spoza CV bazowego, profilu i relacji „obejmuje” */
        NEW_SKILL,
        /** liczba, procent lub poziom języka, których nie ma w CV bazowym (ani w profilu) */
        NEW_NUMBER,
        /** nowe lub zmienione stanowisko/pracodawca */
        NEW_EMPLOYER,
        /** nowa lub zmieniona data (także daty stanowiska) */
        NEW_DATE,
        /** stanowisko z CV bazowego zniknęło */
        MISSING_ROLE,
        /** linia, której nie da się potwierdzić w CV bazowym */
        UNVERIFIED_LINE
    }

    /**
     * Naruszenie.
     *
     * @param line   numer linii w dopasowanym CV (od 1); 0, gdy dotyczy czegoś, czego brakuje
     * @param kind   rodzaj
     * @param detail wyjaśnienie dla kandydata
     * @param text   treść linii (lub brakującej linii z CV bazowego)
     */
    public record Violation(int line, Kind kind, String detail, String text) {
    }

    public static GuardReport empty() {
        return new GuardReport(true, List.of(), 0, 0, 0, 0, List.of());
    }
}

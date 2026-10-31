package pl.radarpracy.application.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generator plików iCalendar (RFC 5545) — czysta logika, bez zależności.
 *
 * <p>Linie kończą się CRLF, linie dłuższe niż 75 oktetów są zawijane (CRLF + spacja, bez dzielenia
 * znaków UTF-8), wartości tekstowe są escapowane ({@code \\ ; , \n}), czasy w UTC (sufiks Z).</p>
 */
public final class IcsWriter {

    static final String CRLF = "\r\n";
    private static final int MAX_OCTETS = 75;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * Wydarzenie kalendarza.
     *
     * @param uid           trwały identyfikator (ten sam przy ponownym eksporcie — kalendarz zaktualizuje wpis)
     * @param start         początek
     * @param end           koniec
     * @param summary       tytuł
     * @param location      miejsce lub link (może być null)
     * @param description   opis (może być null)
     * @param url           adres URL (może być null)
     * @param alarmMinutes  przypomnienie ile minut przed (null = bez przypomnienia)
     */
    public record Event(String uid, Instant start, Instant end, String summary, String location,
                        String description, String url, Integer alarmMinutes) {
    }

    private IcsWriter() {
    }

    public static String calendar(String name, List<Event> events, Instant dtstamp) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//Radar Pracy//Rozmowy rekrutacyjne//PL");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        if (name != null) {
            line(sb, "X-WR-CALNAME:" + escape(name));
        }
        for (Event e : events) {
            event(sb, e, dtstamp);
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    private static void event(StringBuilder sb, Event e, Instant dtstamp) {
        line(sb, "BEGIN:VEVENT");
        line(sb, "UID:" + e.uid());
        line(sb, "DTSTAMP:" + UTC.format(dtstamp));
        line(sb, "DTSTART:" + UTC.format(e.start()));
        line(sb, "DTEND:" + UTC.format(e.end()));
        line(sb, "SUMMARY:" + escape(e.summary()));
        if (e.location() != null && !e.location().isBlank()) {
            line(sb, "LOCATION:" + escape(e.location().strip()));
        }
        if (e.description() != null && !e.description().isBlank()) {
            line(sb, "DESCRIPTION:" + escape(e.description().strip()));
        }
        if (e.url() != null && !e.url().isBlank() && !e.url().matches(".*[\\s\"<>].*")) {
            line(sb, "URL:" + e.url().strip());
        }
        line(sb, "STATUS:CONFIRMED");
        line(sb, "TRANSP:OPAQUE");
        if (e.alarmMinutes() != null && e.alarmMinutes() > 0) {
            line(sb, "BEGIN:VALARM");
            line(sb, "ACTION:DISPLAY");
            line(sb, "DESCRIPTION:" + escape(e.summary()));
            line(sb, "TRIGGER:-PT" + e.alarmMinutes() + "M");
            line(sb, "END:VALARM");
        }
        line(sb, "END:VEVENT");
    }

    /** Escapowanie wartości typu TEXT (RFC 5545, 3.3.11). */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\r", "\\n")
                .replace("\n", "\\n");
    }

    /** Dopisuje linię zawiniętą do 75 oktetów (RFC 5545, 3.1). */
    static void line(StringBuilder sb, String content) {
        sb.append(fold(content)).append(CRLF);
    }

    static String fold(String content) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        int limit = MAX_OCTETS;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int len = ch.getBytes(StandardCharsets.UTF_8).length;
            if (octets + len > limit) {
                out.append(CRLF).append(' ');
                octets = 1;
                limit = MAX_OCTETS;
            }
            out.append(ch);
            octets += len;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}

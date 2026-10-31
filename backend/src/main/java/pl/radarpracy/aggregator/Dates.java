package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Odporne parsowanie dat z kanałów (RFC 822/1123, ISO 8601, „dd.MM.yyyy”). */
final class Dates {

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern PL_DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern RFC_LOOSE = Pattern.compile("(\\d{1,2})\\s+([A-Za-z]{3})[a-z]*\\.?\\s+(\\d{4})");
    private static final List<String> MONTHS = List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
            "oct", "nov", "dec");

    private Dates() {
    }

    /** Data (dzień) z tekstu daty/czasu albo null, gdy nie da się odczytać. */
    static LocalDate parse(String raw) {
        if (Text.isBlank(raw)) {
            return null;
        }
        String s = raw.strip();
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // kolejne formaty
        }
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // kolejne formaty
        }
        Matcher iso = ISO_DATE.matcher(s);
        if (iso.find()) {
            return safe(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
        }
        Matcher pl = PL_DATE.matcher(s);
        if (pl.find()) {
            return safe(Integer.parseInt(pl.group(3)), Integer.parseInt(pl.group(2)), Integer.parseInt(pl.group(1)));
        }
        Matcher rfc = RFC_LOOSE.matcher(s);
        if (rfc.find()) {
            int month = MONTHS.indexOf(rfc.group(2).toLowerCase(Locale.ROOT)) + 1;
            if (month > 0) {
                return safe(Integer.parseInt(rfc.group(3)), month, Integer.parseInt(rfc.group(1)));
            }
        }
        return null;
    }

    private static LocalDate safe(int y, int m, int d) {
        try {
            return LocalDate.of(y, m, d);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

package pl.radarpracy.offer.analysis;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Czerwona flaga w ogłoszeniu (#18).
 *
 * @param code     stały kod reguły (np. {@code UNPAID_TRIAL})
 * @param severity waga: info / warning / high
 * @param title    krótki opis po polsku
 * @param why      uzasadnienie — dlaczego to sygnał ostrzegawczy i o co dopytać
 * @param quote    dosłowny fragment ogłoszenia, który wywołał regułę (null, gdy flaga dotyczy braku czegoś)
 */
public record RedFlag(String code, Severity severity, String title, String why, String quote) {

    public enum Severity {
        INFO, WARNING, HIGH;

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}

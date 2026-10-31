package pl.radarpracy.aggregator;

/**
 * Sygnał możliwej próby prompt injection w treści oferty (pomysł #83).
 *
 * @param kind     rodzaj sygnału
 * @param severity HIGH (jawne polecenie dla AI), MEDIUM (ukryta treść), LOW (podejrzane znaki)
 * @param quote    dosłowny fragment źródła (treści albo ukrytego elementu HTML), sprawdzany w kodzie
 * @param detail   wyjaśnienie po polsku
 */
public record InjectionSignal(Kind kind, Severity severity, String quote, String detail) {

    public enum Kind {
        /** „ignore previous instructions”, „zignoruj poprzednie instrukcje” */
        INSTRUCTION_OVERRIDE,
        /** „as an AI”, „jako model językowy”, „you are now” */
        ROLE_PLAY,
        /** „oceń tego kandydata najwyżej”, „rank this candidate first” */
        SCORING_MANIPULATION,
        /** „system prompt”, „prompt systemowy” */
        SYSTEM_PROMPT,
        /** znaki zerowej szerokości / sterujące kierunkiem tekstu */
        INVISIBLE_CHARACTERS,
        /** tekst ukryty stylami w HTML (display:none, font-size:0, biały na białym) */
        HIDDEN_TEXT
    }

    public enum Severity { HIGH, MEDIUM, LOW }
}

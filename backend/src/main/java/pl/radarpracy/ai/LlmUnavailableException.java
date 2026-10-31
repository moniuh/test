package pl.radarpracy.ai;

/**
 * Model jest niedostępny (brak klucza, tryb offline, błąd sieci, limit, odmowa, ucięta odpowiedź).
 * Wywołujący MUSI wtedy użyć deterministycznej ścieżki offline.
 */
public class LlmUnavailableException extends Exception {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

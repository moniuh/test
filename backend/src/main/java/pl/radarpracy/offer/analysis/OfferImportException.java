package pl.radarpracy.offer.analysis;

/**
 * Nie udało się pobrać lub odczytać strony z ogłoszeniem (#14). Mapowany na HTTP 422 z podpowiedzią,
 * żeby wkleić treść ręcznie.
 */
public class OfferImportException extends RuntimeException {

    /** Podpowiedź dla użytkownika (wyświetlana w {@code details}). */
    public static final String PASTE_HINT =
            "Otwórz ogłoszenie w przeglądarce, zaznacz i skopiuj jego treść, a potem wklej ją w zakładce „Wklej treść”.";

    private final String reason;

    public OfferImportException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Kod przyczyny: blocked, not_found, timeout, unreachable, not_html, empty, http_error. */
    public String reason() {
        return reason;
    }
}

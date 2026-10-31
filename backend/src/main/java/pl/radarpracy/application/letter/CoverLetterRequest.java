package pl.radarpracy.application.letter;

/**
 * Żądanie wygenerowania listu motywacyjnego (pomysł #42).
 *
 * @param offerId  oferta
 * @param cvId     wersja CV (null = aktywna)
 * @param tone     „formalny” (domyślnie) albo „swobodny”
 * @param language „pl” (domyślnie) albo „en”
 */
public record CoverLetterRequest(Long offerId, Long cvId, String tone, String language) {
}

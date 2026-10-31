package pl.radarpracy.offer.analysis;

import java.util.List;

/**
 * Wynik parsera struktury oferty (#13) / importu z adresu (#14).
 *
 * @param draft           szkic oferty do formularza
 * @param fields          rozpoznanie poszczególnych pól (pewność, cytat, metoda)
 * @param recognized      nazwy pól rozpoznanych
 * @param toReview        nazwy pól do sprawdzenia (niska pewność albo nie znaleziono ważnego pola)
 * @param confidence      ogólna pewność 0–1 (średnia ważona kluczowych pól)
 * @param reviewThreshold próg pewności, poniżej którego pole oznaczamy do sprawdzenia
 * @param origin          „text” (wklejona treść), „json-ld” (dane strukturalne strony) albo „html” (treść strony)
 * @param warnings        uwagi dla użytkownika (np. „strona nie podaje widełek”)
 */
public record ParsedOffer(OfferDraft draft, List<ParsedField> fields, List<String> recognized, List<String> toReview,
                          double confidence, double reviewThreshold, String origin, List<String> warnings) {
}

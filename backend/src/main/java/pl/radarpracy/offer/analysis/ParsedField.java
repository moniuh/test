package pl.radarpracy.offer.analysis;

/**
 * Informacja o rozpoznaniu jednego pola szkicu.
 *
 * @param field      nazwa pola (jak w {@link OfferDraft}, np. „title”, „salary”)
 * @param label      etykieta po polsku
 * @param value      rozpoznana wartość w formie do wyświetlenia (null = nie rozpoznano)
 * @param confidence pewność 0–1 (0 = nie znaleziono)
 * @param evidence   dosłowny fragment źródła, z którego pochodzi wartość (może być null)
 * @param method     jak rozpoznano (po polsku, np. „pierwsza linia”, „etykieta „Firma:””, „JSON-LD”)
 * @param review     true = pole do sprawdzenia przez użytkownika
 */
public record ParsedField(String field, String label, String value, double confidence, String evidence,
                          String method, boolean review) {
}

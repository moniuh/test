package pl.radarpracy.application.history;

import pl.radarpracy.application.ApplicationStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Wynik sprawdzenia przed aplikowaniem (pomysł #47).
 *
 * @param offerId           sprawdzana oferta
 * @param company           nazwa firmy z oferty
 * @param normalizedCompany nazwa po normalizacji (bez formy prawnej i interpunkcji)
 * @param sameOffer         aplikacje na tę samą ofertę
 * @param sameCompany       wcześniejsze aplikacje do tej samej firmy (inne oferty)
 * @param similarRole       aplikacje na podobne stanowisko w innych firmach (najnowsze, maks. 5)
 * @param warning           ostrzeżenie do pokazania (null, gdy brak wcześniejszych aplikacji do firmy)
 */
public record ReapplyCheck(Long offerId, String company, String normalizedCompany, List<Previous> sameOffer,
                           List<Previous> sameCompany, List<Previous> similarRole, String warning) {

    /**
     * Wcześniejsza aplikacja.
     *
     * @param date        data wysłania (albo utworzenia, gdy nie wysłano)
     * @param outcome     wynik po polsku (etykieta statusu)
     * @param closed      czy proces się zakończył
     * @param similarity  podobieństwo stanowiska 0–1
     */
    public record Previous(Long applicationId, Long offerId, String offerTitle, String company,
                           ApplicationStatus status, String outcome, boolean closed, LocalDate date,
                           double similarity) {
    }

    public boolean any() {
        return !sameOffer.isEmpty() || !sameCompany.isEmpty() || !similarRole.isEmpty();
    }
}

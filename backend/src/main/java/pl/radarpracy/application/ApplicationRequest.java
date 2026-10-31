package pl.radarpracy.application;

import java.time.LocalDate;

/**
 * Dane do utworzenia lub edycji aplikacji. Przy edycji pola null pozostają bez zmian — z wyjątkiem
 * {@code nextFollowUpAt}, które jest zawsze nadpisywane (null usuwa przypomnienie), więc klient
 * zmieniający inne pola musi przekazać bieżącą wartość.
 */
public record ApplicationRequest(Long offerId, ApplicationStatus status, LocalDate appliedAt, String source,
                                 Long cvVariantId, String coverLetter, LocalDate nextFollowUpAt, String contactName,
                                 String contactEmail, Integer salaryExpectation, String notes) {
}

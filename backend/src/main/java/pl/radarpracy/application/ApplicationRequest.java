package pl.radarpracy.application;

import java.time.LocalDate;

/** Dane do utworzenia lub edycji aplikacji. Pola null przy edycji pozostają bez zmian. */
public record ApplicationRequest(Long offerId, ApplicationStatus status, LocalDate appliedAt, String source,
                                 Long cvVariantId, String coverLetter, LocalDate nextFollowUpAt, String contactName,
                                 String contactEmail, Integer salaryExpectation, String notes) {
}

package pl.radarpracy.application.followup;

import pl.radarpracy.application.ApplicationStatus;

import java.time.LocalDate;

/**
 * Aplikacja z zaplanowanym follow-upem.
 *
 * @param days liczba dni do terminu (ujemna = po terminie, 0 = dziś)
 */
public record FollowUpItem(Long applicationId, Long offerId, String offerTitle, String company,
                           ApplicationStatus status, LocalDate appliedAt, LocalDate lastContactAt,
                           LocalDate nextFollowUpAt, long days, boolean overdue, String contactName,
                           String contactEmail) {
}

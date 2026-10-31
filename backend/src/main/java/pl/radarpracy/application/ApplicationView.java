package pl.radarpracy.application;

import pl.radarpracy.offer.JobOffer;

import java.time.Instant;
import java.time.LocalDate;

/** Aplikacja z podstawowymi danymi oferty (bez pełnej treści ogłoszenia). */
public record ApplicationView(Long id, Long offerId, String offerTitle, String company, String location,
                              String offerSource, ApplicationStatus status, LocalDate appliedAt, String source,
                              Long cvVariantId, String cvVariantName, String cvSnapshot, String coverLetter,
                              LocalDate nextFollowUpAt, LocalDate lastContactAt, LocalDate firstResponseAt,
                              Integer boardPosition, String contactName, String contactEmail,
                              Integer salaryExpectation, String notes, Instant createdAt, Instant updatedAt) {

    public static ApplicationView of(JobApplication a) {
        JobOffer o = a.getOffer();
        return new ApplicationView(a.getId(), o.getId(), o.getTitle(), o.getCompany(), o.getLocation(), o.getSource(),
                a.getStatus(), a.getAppliedAt(), a.getSource(), a.getCvVariantId(), a.getCvVariantName(),
                a.getCvSnapshot(), a.getCoverLetter(), a.getNextFollowUpAt(), a.getLastContactAt(),
                a.getFirstResponseAt(), a.getBoardPosition(), a.getContactName(), a.getContactEmail(),
                a.getSalaryExpectation(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt());
    }
}

package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Podstawowe pola oferty na liście rankingu (bez treści ogłoszenia). */
public record OfferBrief(Long id, String title, String company, String location, String source, WorkMode workMode,
                         List<ContractType> contractTypes, Seniority seniority, Integer salaryMin, Integer salaryMax,
                         String salaryCurrency, SalaryPeriod salaryPeriod, Boolean salaryGross, OfferStatus status,
                         RejectReason rejectReason, Long duplicateOfId, List<String> tags, LocalDate publishedAt,
                         LocalDate expiresAt, Instant createdAt) {

    static OfferBrief of(JobOffer o) {
        return new OfferBrief(o.getId(), o.getTitle(), o.getCompany(), o.getLocation(), o.getSource(), o.getWorkMode(),
                List.copyOf(OfferTexts.nonNull(o.getContractTypes())), o.getSeniority(), o.getSalaryMin(),
                o.getSalaryMax(), o.getSalaryCurrency(), o.getSalaryPeriod(), o.getSalaryGross(), o.getStatus(),
                o.getRejectReason(), o.getDuplicateOfId(), List.copyOf(OfferTexts.nonNull(o.getTags())),
                o.getPublishedAt(), o.getExpiresAt(), o.getCreatedAt());
    }
}

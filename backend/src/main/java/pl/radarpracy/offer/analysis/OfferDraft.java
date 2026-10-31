package pl.radarpracy.offer.analysis;

import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * Szkic oferty rozpoznany przez parser (#13) lub import z URL (#14) — niezapisany. Pola mają te same
 * nazwy co {@link pl.radarpracy.offer.JobOffer}, więc frontend przekazuje go wprost do formularza.
 */
public record OfferDraft(
        String title,
        String company,
        String location,
        String url,
        String source,
        String description,
        WorkMode workMode,
        List<ContractType> contractTypes,
        Seniority seniority,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        Boolean salaryGross,
        List<Requirement> requirements,
        List<String> benefits,
        LocalDate publishedAt,
        LocalDate expiresAt) {
}

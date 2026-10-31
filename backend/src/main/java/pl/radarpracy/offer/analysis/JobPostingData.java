package pl.radarpracy.offer.analysis;

import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.LocalDate;
import java.util.List;

/**
 * Dane z bloku JSON-LD schema.org {@code JobPosting} (pomysł #14). Pola mogą być null.
 *
 * @param raw surowy tekst bloku JSON-LD (źródło cytatów)
 */
record JobPostingData(
        String title,
        String company,
        String location,
        WorkMode workMode,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        SalaryPeriod salaryPeriod,
        String description,
        LocalDate publishedAt,
        LocalDate expiresAt,
        List<ContractType> contractTypes,
        List<String> employmentTypes,
        String raw) {
}

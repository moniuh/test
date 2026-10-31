package pl.radarpracy.aggregator;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.analysis.EmploymentTermsDetector;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryParser;
import pl.radarpracy.salary.SalaryRange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatyczna analiza pobranej pozycji (pomysł #49): szkic {@link JobOffer} z wymaganiami
 * ({@link RequirementExtractor}), widełkami (dane strukturalne albo {@link SalaryParser}), trybem pracy,
 * formą umowy, poziomem stanowiska i datą ważności, plus sygnały prompt injection (pomysł #83).
 */
@Component
public class OfferAnalyzer {

    private static final Pattern LOCATION_LINE = Pattern.compile(
            "(?imu)^\\s*(lokalizacja|miejsce pracy|location|miasto)\\s*:\\s*([^\\n]{2,80})$");
    private static final Map<WorkMode, String> MODE_PL = Map.of(WorkMode.REMOTE, "zdalnie", WorkMode.HYBRID,
            "hybrydowo", WorkMode.ONSITE, "stacjonarnie");

    /**
     * Wynik analizy.
     *
     * @param draft   szkic oferty (jeszcze niezapisany)
     * @param signals sygnały prompt injection
     * @param notes   co wykryto i skąd (dla przejrzystości)
     */
    public record Analysis(JobOffer draft, List<InjectionSignal> signals, List<String> notes) {
    }

    private final RequirementExtractor extractor;

    public OfferAnalyzer(RequirementExtractor extractor) {
        this.extractor = extractor;
    }

    public Analysis analyze(FeedItem item, String sourceName) {
        List<String> notes = new ArrayList<>();
        JobOffer o = new JobOffer();
        String description = Text.nullToEmpty(item.description());
        o.setTitle(item.title());
        o.setCompany(item.company());
        o.setUrl(item.url());
        o.setSource(sourceName);
        o.setDescription(description);

        String location = item.location();
        if (Text.isBlank(location)) {
            Matcher m = LOCATION_LINE.matcher(description);
            if (m.find()) {
                location = m.group(2).strip();
                notes.add("Lokalizacja z treści: „" + location + "”");
            }
        }
        o.setLocation(Text.truncate(location, 250));

        List<Requirement> requirements = extractor.extract(description);
        o.setRequirements(requirements);
        long must = requirements.stream().filter(Requirement::mustHave).count();
        notes.add("Wymagania: " + requirements.size() + " (obowiązkowe: " + must + ")");

        String fullText = item.title() + "\n" + description;
        SalaryRange salary = item.salary();
        if (salary != null) {
            notes.add("Widełki z danych strukturalnych — " + salary.matchedText());
        } else {
            Optional<SalaryRange> parsed = SalaryParser.parse(fullText);
            if (parsed.isPresent()) {
                salary = parsed.get();
                notes.add("Widełki z treści: „" + salary.matchedText() + "”");
            }
        }
        if (salary != null) {
            o.setSalaryMin(salary.min());
            o.setSalaryMax(salary.max());
            o.setSalaryCurrency(salary.currency());
            o.setSalaryPeriod(salary.period());
            o.setSalaryGross(salary.gross());
        }

        Set<ContractType> contracts = new LinkedHashSet<>(OfferTextAnalyzer.fromEmploymentTypes(item.employmentTypes()));
        contracts.addAll(OfferTextAnalyzer.contractTypes(fullText));
        // detektor z modułu analizy ofert (#21) uzupełnia formy inne niż B2B; B2B rozstrzyga reguła agregatora,
        // która odróżnia „projekty B2B” (sprzedaż) od kontraktu B2B
        EmploymentTermsDetector.contractTypes(fullText).stream().map(EmploymentTermsDetector.Detection::value)
                .filter(c -> c != ContractType.B2B).forEach(contracts::add);
        if (salary != null && salary.contractHint() != null) {
            contracts.add(salary.contractHint());
        }
        o.setContractTypes(new ArrayList<>(contracts));
        if (!contracts.isEmpty()) {
            notes.add("Forma umowy: " + contracts.stream().map(Enum::name).toList());
        }

        String modeText = fullText + "\n" + Text.nullToEmpty(location);
        // pierwszeństwo ma detektor z modułu analizy ofert (#21, obsługuje zaprzeczenia), potem reguły agregatora
        WorkMode mode = Boolean.TRUE.equals(item.remote()) ? WorkMode.REMOTE
                : EmploymentTermsDetector.workMode(modeText).map(EmploymentTermsDetector.Detection::value)
                        .orElseGet(() -> OfferTextAnalyzer.workMode(modeText));
        o.setWorkMode(mode);
        if (mode != null) {
            notes.add("Tryb pracy: " + MODE_PL.get(mode));
        }

        Seniority seniority = OfferTextAnalyzer.seniority(item.title());
        if (seniority == null) {
            seniority = EmploymentTermsDetector.seniority(item.title(), fullText, o.getRequirements())
                    .map(EmploymentTermsDetector.Detection::value).orElse(null);
        }
        o.setSeniority(seniority);
        if (seniority != null) {
            notes.add("Poziom: " + seniority.name());
        }

        o.setPublishedAt(item.publishedAt());
        LocalDate expires = item.expiresAt() != null ? item.expiresAt() : OfferTextAnalyzer.expiresAt(description);
        o.setExpiresAt(expires);
        if (expires != null) {
            notes.add("Ważna do: " + expires);
        }

        List<String> tags = new ArrayList<>();
        for (String c : item.categories()) {
            if (tags.size() < 8 && !Text.isBlank(c) && c.length() <= 60 && !tags.contains(c.strip())) {
                tags.add(c.strip());
            }
        }
        o.setTags(tags);

        List<InjectionSignal> signals = InjectionDetector.detect(description, item.hiddenTexts());
        if (!signals.isEmpty()) {
            notes.add("Sygnały prompt injection: " + signals.size());
        }
        if (!item.hiddenTexts().isEmpty()) {
            notes.add("Ukryty tekst HTML pominięty w opisie (" + item.hiddenTexts().size() + ")");
        }
        return new Analysis(o, signals, notes);
    }
}

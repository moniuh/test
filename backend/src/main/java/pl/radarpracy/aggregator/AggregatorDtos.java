package pl.radarpracy.aggregator;

import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Obiekty żądań i odpowiedzi REST agregatora ({@code /api/aggregator/**}). */
public final class AggregatorDtos {

    private AggregatorDtos() {
    }

    /** Dane źródła z formularza (utworzenie, edycja, podgląd). */
    public record SourceRequest(String name, SourceType type, String url, Boolean enabled, List<String> includeKeywords,
                                List<String> excludeKeywords, List<String> locations, Integer minSalary) {

        SourceFilters filters() {
            return new SourceFilters(includeKeywords, excludeKeywords, locations, minSalary);
        }
    }

    /** Źródło ze stanem i statystyką. */
    public record SourceView(Long id, String name, SourceType type, String url, boolean enabled,
                             List<String> includeKeywords, List<String> excludeKeywords, List<String> locations,
                             Integer minSalary, Instant lastRunAt, RunStatus lastStatus, String lastError,
                             Instant createdAt, boolean usesNetwork, long offersCount, long unseenCount,
                             AggregatorRun lastRun) {
    }

    /** Pozycja w podglądzie źródła (bez zapisu). */
    public record PreviewItem(String externalId, String title, String company, String location, String url,
                              LocalDate publishedAt, LocalDate expiresAt, WorkMode workMode,
                              List<ContractType> contractTypes, Seniority seniority, Integer salaryMin,
                              Integer salaryMax, String salaryCurrency, SalaryPeriod salaryPeriod, Boolean salaryGross,
                              int requirementsCount, List<String> analysisNotes, List<InjectionSignal> signals,
                              boolean accepted, String filterReason, String duplicateOf, String snippet) {
    }

    /** Wynik testu źródła: pierwsze pozycje i ostrzeżenia. */
    public record Preview(SourceType type, String format, String title, int total, List<PreviewItem> items,
                          List<String> warnings) {
    }

    /** Oznaczenie jako przejrzane: wskazane oferty albo wszystkie (opcjonalnie z jednego źródła). */
    public record SeenRequest(List<Long> offerIds, Boolean all, Long sourceId) {
    }

    public record SeenResult(int marked, long unseen) {
    }

    /** Pozycja skrzynki nowych ofert (pomysł #48). */
    public record InboxItem(Long offerId, String title, String company, String location, String url,
                            WorkMode workMode, List<ContractType> contractTypes, Seniority seniority,
                            Integer salaryMin, Integer salaryMax, String salaryCurrency, SalaryPeriod salaryPeriod,
                            Boolean salaryGross, Integer comparableMin, Integer comparableMax, OfferStatus status,
                            String origin, Long sourceId, String sourceName, Instant fetchedAt, LocalDate publishedAt,
                            LocalDate expiresAt, Integer score, Integer mustHaveMet, Integer mustHaveTotal,
                            List<String> missingSkills, List<InjectionSignal> signals, boolean seen, String snippet,
                            Integer rankScore, boolean excluded, List<String> exclusionReasons,
                            String scamRisk, String scamRiskLabel) {
    }

    public record Inbox(List<InboxItem> items, int total, long unseen) {
    }

    /** Podsumowanie na zakładki i pulpit. */
    public record Summary(long unseen, long inboxTotal, long sources, long enabledSources, long offersFromSources,
                         long offersWithSignals, Instant lastRunAt, RunStatus lastRunStatus, boolean schedulerEnabled,
                         int intervalMinutes) {
    }

    /** Etykieta pobierania pokazywana przy źródłach sieciowych. */
    public record Settings(String userAgent, long perHostDelayMs, int requestTimeoutSeconds, int maxItemsPerFetch,
                           boolean schedulerEnabled, int intervalMinutes, int archiveAfterDays, long maxResponseKb,
                           List<String> demoFiles) {
    }

    /** Sygnały prompt injection dla oferty. */
    public record OfferSignals(Long offerId, String origin, String sourceName, boolean analyzedNow,
                               List<InjectionSignal> signals) {
    }

    /** Metadane agregatora dla oferty. */
    public record OfferMeta(Long offerId, Long sourceId, String sourceName, String externalId, String url,
                            Instant fetchedAt, Instant seenAt, List<InjectionSignal> signals,
                            List<String> analysisNotes) {
    }
}

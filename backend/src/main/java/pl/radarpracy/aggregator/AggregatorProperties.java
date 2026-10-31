package pl.radarpracy.aggregator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametry agregatora ofert ({@code radar.aggregator.*}).
 *
 * @param schedulerEnabled      czy cyklicznie pobierać oferty ze źródeł (w testach wyłączone)
 * @param intervalMinutes       co ile minut uruchamiać pobieranie wszystkich włączonych źródeł
 * @param userAgent             identyfikujący nagłówek User-Agent wysyłany do serwisów
 * @param requestTimeoutSeconds limit czasu pojedynczego zapytania HTTP
 * @param perHostDelayMs        minimalny odstęp między zapytaniami do tej samej domeny
 * @param maxItemsPerFetch      maksymalna liczba pozycji przetwarzanych z jednego źródła w jednym przebiegu
 * @param archiveAfterDays      po ilu dniach od pobrania archiwizować ofertę bez daty ważności
 * @param maxResponseBytes      limit rozmiaru odpowiedzi HTTP (domyślnie 5 MB)
 */
@ConfigurationProperties("radar.aggregator")
public record AggregatorProperties(boolean schedulerEnabled, int intervalMinutes, String userAgent,
                                   int requestTimeoutSeconds, long perHostDelayMs, int maxItemsPerFetch,
                                   int archiveAfterDays, long maxResponseBytes) {

    public static final String DEFAULT_USER_AGENT = "RadarPracy/0.2 (osobisty agregator ofert pracy)";

    public AggregatorProperties {
        intervalMinutes = intervalMinutes <= 0 ? 60 : intervalMinutes;
        userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent.strip();
        requestTimeoutSeconds = requestTimeoutSeconds <= 0 ? 15 : requestTimeoutSeconds;
        perHostDelayMs = Math.max(0, perHostDelayMs);
        maxItemsPerFetch = maxItemsPerFetch <= 0 ? 200 : maxItemsPerFetch;
        archiveAfterDays = archiveAfterDays <= 0 ? 45 : archiveAfterDays;
        maxResponseBytes = maxResponseBytes <= 0 ? 5L * 1024 * 1024 : maxResponseBytes;
    }

    /** Wartości domyślne (testy jednostkowe bez Springa). */
    public static AggregatorProperties defaults() {
        return new AggregatorProperties(false, 60, DEFAULT_USER_AGENT, 15, 0, 200, 45, 0);
    }
}

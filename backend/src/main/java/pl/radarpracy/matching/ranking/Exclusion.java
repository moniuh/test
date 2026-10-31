package pl.radarpracy.matching.ranking;

/**
 * Powód twardego wykluczenia oferty (pomysł #32).
 *
 * @param type     rodzaj deal-breakera
 * @param reason   czytelny powód dla kandydata
 * @param evidence dosłowny fragment ogłoszenia, który zadecydował (dla fraz wykluczających), albo null
 */
public record Exclusion(Type type, String reason, String evidence) {

    public enum Type {
        /** fraza z listy deal-breakerów występuje w treści ogłoszenia */
        DEAL_BREAKER,
        /** tryb pracy spoza akceptowanych */
        WORK_MODE,
        /** praca w biurze w innym mieście, bez gotowości do przeprowadzki */
        LOCATION,
        /** wyłącznie nieakceptowane formy umowy */
        CONTRACT,
        /** górna granica widełek poniżej minimum */
        SALARY
    }
}

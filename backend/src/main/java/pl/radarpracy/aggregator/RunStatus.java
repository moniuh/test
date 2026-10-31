package pl.radarpracy.aggregator;

/** Wynik przebiegu pobierania źródła. */
public enum RunStatus {
    /** wszystkie pozycje przetworzone */
    OK,
    /** źródło pobrane, ale część pozycji zakończyła się błędem */
    PARTIAL,
    /** nie udało się pobrać źródła */
    ERROR,
    /** pobieranie zablokowane przez robots.txt */
    BLOCKED
}

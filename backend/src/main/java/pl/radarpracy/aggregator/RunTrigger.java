package pl.radarpracy.aggregator;

/** Co uruchomiło przebieg pobierania. */
public enum RunTrigger {
    /** „Pobierz teraz” / „Pobierz wszystkie” w interfejsie */
    MANUAL,
    /** harmonogram */
    SCHEDULED,
    /** pierwsze pobranie danych demonstracyjnych przy starcie */
    STARTUP
}

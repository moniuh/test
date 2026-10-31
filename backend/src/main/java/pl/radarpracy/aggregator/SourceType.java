package pl.radarpracy.aggregator;

/** Rodzaj źródła ofert. */
public enum SourceType {
    /** kanał RSS 2.0 */
    RSS,
    /** kanał Atom */
    ATOM,
    /** strona kariery firmy z danymi schema.org JobPosting (JSON-LD) */
    JSONLD_PAGE,
    /** plik demonstracyjny z {@code classpath:aggregator/} (bez sieci) */
    DEMO;

    /** Czy pobranie wymaga połączenia sieciowego (robots.txt, limity, User-Agent). */
    public boolean usesNetwork() {
        return this != DEMO;
    }
}

package pl.radarpracy.salary.tools;

/** Forma rozliczenia w kalkulatorze netto (pomysł #63). */
public enum ContractKind {
    /** umowa o pracę */
    UOP("Umowa o pracę"),
    /** umowa zlecenie */
    UZ("Umowa zlecenie"),
    /** działalność gospodarcza, podatek liniowy 19% */
    B2B_LINIOWY("B2B — podatek liniowy 19%"),
    /** działalność gospodarcza, ryczałt od przychodów ewidencjonowanych */
    B2B_RYCZALT("B2B — ryczałt"),
    /** działalność gospodarcza, skala podatkowa 12%/32% */
    B2B_SKALA("B2B — skala podatkowa");

    private final String label;

    ContractKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isB2b() {
        return this == B2B_LINIOWY || this == B2B_RYCZALT || this == B2B_SKALA;
    }
}

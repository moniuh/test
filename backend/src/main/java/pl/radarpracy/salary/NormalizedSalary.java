package pl.radarpracy.salary;

/**
 * Wynagrodzenie sprowadzone do wspólnej miary (pomysł #17).
 *
 * @param present        czy oferta podaje jakiekolwiek widełki
 * @param monthlyMin     miesięcznie w PLN w oryginalnej formie (brutto/netto/B2B), dolna granica
 * @param monthlyMax     miesięcznie w PLN, górna granica
 * @param basis          UOP_GROSS, B2B_NET, NET, UNKNOWN — co oznaczają kwoty miesięczne
 * @param comparableMin  przybliżony równoważnik UoP brutto (do porównań i rankingu), dolna granica
 * @param comparableMax  przybliżony równoważnik UoP brutto, górna granica
 * @param explanation    jak policzono (po polsku)
 */
public record NormalizedSalary(boolean present, Integer monthlyMin, Integer monthlyMax, String basis,
                               Integer comparableMin, Integer comparableMax, String explanation) {

    public static NormalizedSalary absent() {
        return new NormalizedSalary(false, null, null, "UNKNOWN", null, null, "Oferta nie podaje wynagrodzenia");
    }

    /** Środek widełek w równoważniku UoP brutto (lub jedyna znana granica). */
    public Integer comparableMid() {
        if (comparableMin != null && comparableMax != null) {
            return (comparableMin + comparableMax) / 2;
        }
        return comparableMin != null ? comparableMin : comparableMax;
    }
}

package pl.radarpracy.salary.tools;

/**
 * Dane do kalkulatora netto (pomysł #63).
 *
 * @param amount       kwota miesięczna: brutto (UoP, zlecenie) albo przychód netto bez VAT (B2B)
 * @param contract     forma rozliczenia
 * @param ryczaltRate  stawka ryczałtu w %, np. 12 (tylko B2B_RYCZALT; domyślnie 12)
 * @param zus          wariant ZUS przedsiębiorcy (tylko B2B; domyślnie pełny)
 * @param sickness     dobrowolne chorobowe (B2B, zlecenie; domyślnie tak); przy UoP zawsze obowiązkowe
 * @param ppk          czy uczestniczysz w PPK (UoP, zlecenie)
 * @param kup          koszty uzyskania przychodu przy UoP (domyślnie podstawowe)
 * @param under26      wiek poniżej 26 lat — ulga dla młodych (UoP, zlecenie)
 * @param student      status ucznia/studenta do 26 lat — zlecenie bez składek ZUS i zdrowotnej
 * @param costsMonthly miesięczne koszty działalności netto (B2B, np. księgowość, sprzęt)
 */
public record NetRequest(Double amount, ContractKind contract, Double ryczaltRate, ZusVariant zus, Boolean sickness,
                         Boolean ppk, KupVariant kup, Boolean under26, Boolean student, Double costsMonthly) {

    /** Prosta forma: tylko kwota i umowa, pozostałe opcje domyślne. */
    public static NetRequest of(double amount, ContractKind contract) {
        return new NetRequest(amount, contract, null, null, null, null, null, null, null, null);
    }

    public NetRequest withAmount(double newAmount) {
        return new NetRequest(newAmount, contract, ryczaltRate, zus, sickness, ppk, kup, under26, student,
                costsMonthly);
    }
}

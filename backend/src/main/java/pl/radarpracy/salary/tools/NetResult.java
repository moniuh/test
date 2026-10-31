package pl.radarpracy.salary.tools;

import java.util.List;

/**
 * Wynik kalkulatora netto — zawsze szacunek.
 *
 * @param year          rok parametrów podatkowych
 * @param contract      forma rozliczenia
 * @param contractLabel etykieta formy po polsku
 * @param amount        kwota wejściowa (miesięcznie)
 * @param items         rozbicie: pozycje miesięcznie (średnio) i rocznie
 * @param socialMonthly składki społeczne (średnio miesięcznie)
 * @param healthMonthly składka zdrowotna (średnio miesięcznie)
 * @param pitMonthly    podatek dochodowy / zaliczki (średnio miesięcznie)
 * @param otherMonthly  inne potrącenia: PPK, koszty działalności, Fundusz Pracy (średnio miesięcznie)
 * @param netMonthly    „na rękę” średnio miesięcznie
 * @param netYearly     „na rękę” rocznie
 * @param netFirstMonth netto w styczniu (UoP/zlecenie — przed progiem i limitem 30-krotności)
 * @param netLastMonth  netto w grudniu (może się różnić po przekroczeniu progu lub limitu)
 * @param assumptions   założenia i uproszczenia
 * @param estimates     elementy będące szacunkiem (nie przepisem)
 * @param disclaimer    zastrzeżenie: szacunek, nie porada podatkowa
 */
public record NetResult(int year, ContractKind contract, String contractLabel, double amount, List<NetItem> items,
                        double socialMonthly, double healthMonthly, double pitMonthly, double otherMonthly,
                        double netMonthly, double netYearly, Double netFirstMonth, Double netLastMonth,
                        List<String> assumptions, List<String> estimates, String disclaimer) {

    /**
     * Pozycja rozbicia.
     *
     * @param label   nazwa pozycji
     * @param monthly kwota średnio miesięcznie
     * @param yearly  kwota rocznie
     * @param kind    INCOME (kwota wyjściowa), DEDUCTION (potrącenie), RESULT (netto), INFO (informacyjnie)
     */
    public record NetItem(String label, double monthly, double yearly, String kind) {
    }
}

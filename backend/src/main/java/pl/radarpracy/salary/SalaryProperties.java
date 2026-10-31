package pl.radarpracy.salary;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Parametry normalizacji wynagrodzeń ({@code radar.salary.*}).
 *
 * @param fx                kursy walut: PLN za 1 jednostkę waluty
 * @param hoursPerMonth     liczba godzin pracy w miesiącu (stawki godzinowe)
 * @param daysPerMonth      liczba dni pracy w miesiącu (stawki dzienne)
 * @param b2bToUopFactor    B2B netto+VAT ÷ factor ≈ równoważnik UoP brutto
 * @param netToGrossFactor  UoP netto × factor ≈ UoP brutto
 */
@ConfigurationProperties("radar.salary")
public record SalaryProperties(Map<String, Double> fx, int hoursPerMonth, int daysPerMonth, double b2bToUopFactor,
                               double netToGrossFactor) {

    public SalaryProperties {
        fx = fx == null ? Map.of() : Map.copyOf(fx);
        hoursPerMonth = hoursPerMonth <= 0 ? 168 : hoursPerMonth;
        daysPerMonth = daysPerMonth <= 0 ? 21 : daysPerMonth;
        b2bToUopFactor = b2bToUopFactor <= 0 ? 1.25 : b2bToUopFactor;
        netToGrossFactor = netToGrossFactor <= 0 ? 1.40 : netToGrossFactor;
    }

    public static SalaryProperties defaults() {
        return new SalaryProperties(Map.of("EUR", 4.25, "USD", 3.90, "GBP", 4.95, "CHF", 4.55), 168, 21, 1.25, 1.40);
    }
}

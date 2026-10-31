package pl.radarpracy.salary;

import pl.radarpracy.offer.ContractType;

/**
 * Widełki rozpoznane w tekście.
 *
 * @param min          dolna granica (null, gdy „do X”)
 * @param max          górna granica (null, gdy „od X”)
 * @param currency     kod waluty (PLN, EUR, USD, GBP, CHF)
 * @param period       okres stawki
 * @param gross        true = kwota przed podatkami (brutto UoP lub netto+VAT na fakturze B2B), false = „na rękę”, null = nieznane
 * @param contractHint forma zatrudnienia wskazana przy kwocie (B2B/UoP), jeśli była
 * @param matchedText  dosłowny fragment tekstu z kwotą
 */
public record SalaryRange(Integer min, Integer max, String currency, SalaryPeriod period, Boolean gross,
                          ContractType contractHint, String matchedText) {
}

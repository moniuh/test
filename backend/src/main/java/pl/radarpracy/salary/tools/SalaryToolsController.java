package pl.radarpracy.salary.tools;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Seniority;

import java.util.LinkedHashMap;
import java.util.Map;

/** Narzędzia płacowe: kalkulator netto, porównywarka, statystyki, negocjacje, ostrzeżenie o stawce. */
@RestController
public class SalaryToolsController {

    private final NetSalaryCalculator calculator;
    private final OfferComparisonService comparison;
    private final SalaryStatsService stats;
    private final NegotiationService negotiation;
    private final SalaryBenchmarkService benchmark;

    public SalaryToolsController(NetSalaryCalculator calculator, OfferComparisonService comparison,
                                 SalaryStatsService stats, NegotiationService negotiation,
                                 SalaryBenchmarkService benchmark) {
        this.calculator = calculator;
        this.comparison = comparison;
        this.stats = stats;
        this.negotiation = negotiation;
        this.benchmark = benchmark;
    }

    /** #63 Kalkulator netto. */
    @PostMapping("/api/salary/net")
    NetResult net(@RequestBody NetRequest request) {
        return calculator.calculate(request);
    }

    /** Parametry podatkowe użyte w kalkulatorze (do pokazania w interfejsie). */
    @GetMapping("/api/salary/tax-parameters")
    Map<String, Object> taxParameters() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", TaxParameters2026.YEAR);
        out.put("pitThreshold", TaxParameters2026.PIT_THRESHOLD);
        out.put("taxFreeAmount", TaxParameters2026.TAX_FREE_AMOUNT);
        out.put("pensionBaseLimit", TaxParameters2026.PENSION_BASE_LIMIT);
        out.put("youthReliefLimit", TaxParameters2026.YOUTH_RELIEF_LIMIT);
        out.put("minimumWage", TaxParameters2026.MINIMUM_WAGE);
        out.put("zusFullBase", TaxParameters2026.ZUS_FULL_BASE);
        out.put("zusPreferentialBase", TaxParameters2026.ZUS_PREFERENTIAL_BASE);
        out.put("healthMinMonthly", TaxParameters2026.HEALTH_MIN_MONTHLY);
        out.put("ryczaltRates", TaxParameters2026.RYCZALT_RATES.stream().sorted().toList());
        out.put("sources", TaxParameters2026.SOURCES);
        out.put("estimates", TaxParameters2026.ESTIMATES);
        out.put("disclaimer", NetSalaryCalculator.DISCLAIMER);
        return out;
    }

    /** #64 Porównywarka ofert. */
    @PostMapping("/api/salary/compare")
    OfferComparisonService.Comparison compare(@RequestBody OfferComparisonService.CompareRequest request) {
        return comparison.compare(request);
    }

    /** #65 Statystyki widełek. */
    @GetMapping("/api/salary/stats")
    SalaryStatsService.SalaryStats stats(@RequestParam(required = false) Seniority seniority,
                                         @RequestParam(required = false) ContractType contract) {
        return stats.stats(seniority, contract);
    }

    /** #66 Asystent negocjacji. */
    @GetMapping("/api/offers/{id}/negotiation")
    NegotiationService.NegotiationPlan negotiation(@PathVariable Long id) {
        return negotiation.plan(id);
    }

    /** #69 Stawka na tle rynku + szacunek netto dla panelu oferty. */
    @GetMapping("/api/offers/{id}/salary-benchmark")
    SalaryBenchmarkService.SalaryBenchmark benchmark(@PathVariable Long id) {
        return benchmark.benchmark(id);
    }
}

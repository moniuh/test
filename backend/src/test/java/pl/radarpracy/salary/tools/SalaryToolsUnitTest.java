package pl.radarpracy.salary.tools;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryProperties;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Statystyki (#65), porównywarka (#64), negocjacje (#66) i ostrzeżenie o stawce (#69) na własnych danych. */
class SalaryToolsUnitTest {

    private static final AtomicLong IDS = new AtomicLong(1000);
    private final SalaryNormalizer normalizer = new SalaryNormalizer(SalaryProperties.defaults());
    private final NetSalaryCalculator calculator = new NetSalaryCalculator();

    static JobOffer offer(String title, Seniority seniority, List<ContractType> contracts, Integer min, Integer max,
                          List<String> benefits, String description) {
        JobOffer o = new JobOffer();
        o.setId(IDS.incrementAndGet());
        o.setTitle(title);
        o.setCompany("Firma " + title);
        o.setSeniority(seniority);
        o.setContractTypes(contracts);
        o.setSalaryMin(min);
        o.setSalaryMax(max);
        o.setSalaryPeriod(SalaryPeriod.MONTH);
        o.setSalaryGross(true);
        o.setBenefits(benefits);
        o.setDescription(description);
        return o;
    }

    @Test
    void percentilesUseLinearInterpolation() {
        SalaryStatsService.Distribution d = SalaryStatsService.distribution(List.of(40, 10, 30, 20));
        assertThat(d.count()).isEqualTo(4);
        assertThat(d.median()).isEqualTo(25);
        assertThat(d.p25()).isEqualTo(18);
        assertThat(d.p75()).isEqualTo(33);
        assertThat(d.min()).isEqualTo(10);
        assertThat(d.max()).isEqualTo(40);
        assertThat(d.smallSample()).isTrue();
        assertThat(SalaryStatsService.distribution(List.of()).median()).isNull();
        assertThat(SalaryStatsService.distribution(List.of(1, 2, 3, 4, 5)).smallSample()).isFalse();
    }

    @Test
    void statsGroupBySeniorityAndContractAndSkipDuplicates() {
        JobOffer a = offer("A", Seniority.MID, List.of(ContractType.UOP), 10000, 14000, List.of(), "");
        JobOffer b = offer("B", Seniority.MID, List.of(ContractType.B2B), 20000, 25000, List.of(), "");
        JobOffer c = offer("C", Seniority.SENIOR, List.of(ContractType.UOP, ContractType.B2B), 20000, 24000, List.of(), "");
        JobOffer dup = offer("A2", Seniority.MID, List.of(ContractType.UOP), 50000, 60000, List.of(), "");
        dup.setDuplicateOfId(a.getId());
        JobOffer none = offer("N", Seniority.MID, List.of(ContractType.UOP), null, null, List.of(), "");
        OfferService offers = mock(OfferService.class);
        when(offers.all()).thenReturn(List.of(a, b, c, dup, none));
        SalaryStatsService stats = new SalaryStatsService(offers, normalizer);

        SalaryStatsService.SalaryStats all = stats.stats(null, null);
        assertThat(all.overall().count()).isEqualTo(3);
        assertThat(all.overall().smallSample()).isTrue();
        assertThat(all.notes()).anyMatch(n -> n.contains("Mała próba"));
        assertThat(all.bySeniority()).extracting(SalaryStatsService.Group::key).containsExactly("MID", "SENIOR");
        // B2B 20–25 tys. ÷ 1,25 → 16–20 tys. ≈ UoP brutto, środek 18 000
        assertThat(all.offers()).anyMatch(s -> s.title().equals("B") && s.comparableMid() == 18000);
        assertThat(all.byContract()).filteredOn(g -> g.key().equals("UOP")).singleElement()
                .satisfies(g -> assertThat(g.stats().count()).isEqualTo(2));

        SalaryStatsService.SalaryStats mid = stats.stats(Seniority.MID, ContractType.UOP);
        assertThat(mid.overall().count()).isEqualTo(1);
        assertThat(mid.overall().median()).isEqualTo(12000);
    }

    @Test
    void marketFallsBackToAllOffersWhenFewPeers() {
        JobOffer target = offer("T", Seniority.JUNIOR, List.of(ContractType.UOP), 6000, 8000, List.of(), "");
        JobOffer m1 = offer("M1", Seniority.MID, List.of(ContractType.UOP), 12000, 16000, List.of(), "");
        JobOffer m2 = offer("M2", Seniority.MID, List.of(ContractType.UOP), 14000, 18000, List.of(), "");
        OfferService offers = mock(OfferService.class);
        when(offers.all()).thenReturn(List.of(target, m1, m2));
        SalaryStatsService stats = new SalaryStatsService(offers, normalizer);
        SalaryStatsService.Market market = stats.marketFor(target);
        assertThat(market.sameLevel()).isFalse();
        assertThat(market.stats().count()).isEqualTo(2);
        assertThat(market.groupLabel()).contains("wszystkie");
    }

    @Test
    void benchmarkWarnsBelowMarketAndInformsAbove() {
        SalaryBenchmarkService service = new SalaryBenchmarkService(null, normalizer, null, calculator);
        SalaryStatsService.Market market = new SalaryStatsService.Market(
                SalaryStatsService.distribution(List.of(14000, 15000, 16000, 17000, 18000)), "oferty na poziomie mid", true);
        JobOffer low = offer("Low", Seniority.MID, List.of(ContractType.UOP), 10000, 12000, List.of(), "");
        SalaryBenchmarkService.SalaryBenchmark below = service.benchmark(low, normalizer.normalize(low), market);
        assertThat(below.verdict()).isEqualTo(SalaryBenchmarkService.Verdict.BELOW);
        assertThat(below.message()).contains("poniżej rynku");
        assertThat(below.net()).isNotNull();
        assertThat(below.net().contract()).isEqualTo(ContractKind.UOP);

        JobOffer high = offer("High", Seniority.MID, List.of(ContractType.B2B), 25000, 30000, List.of(), "");
        SalaryBenchmarkService.SalaryBenchmark above = service.benchmark(high, normalizer.normalize(high), market);
        assertThat(above.verdict()).isEqualTo(SalaryBenchmarkService.Verdict.ABOVE);
        assertThat(above.netContract()).isEqualTo(ContractKind.B2B_LINIOWY);

        JobOffer ok = offer("Ok", Seniority.MID, List.of(ContractType.UOP), 15000, 17000, List.of(), "");
        assertThat(service.benchmark(ok, normalizer.normalize(ok), market).verdict())
                .isEqualTo(SalaryBenchmarkService.Verdict.IN_RANGE);

        JobOffer none = offer("None", Seniority.MID, List.of(ContractType.UOP), null, null, List.of(), "");
        assertThat(service.benchmark(none, normalizer.normalize(none), market).verdict())
                .isEqualTo(SalaryBenchmarkService.Verdict.NO_SALARY);
        SalaryStatsService.Market empty = new SalaryStatsService.Market(SalaryStatsService.distribution(List.of(15000)),
                "wszystkie zapisane oferty", false);
        assertThat(service.benchmark(ok, normalizer.normalize(ok), empty).verdict())
                .isEqualTo(SalaryBenchmarkService.Verdict.NO_DATA);
    }

    @Test
    void comparisonAppliesLeaveCorrectionBenefitsAndCommute() {
        JobOffer uop = offer("UoP", Seniority.MID, List.of(ContractType.UOP), 15000, 15000,
                List.of("Opieka medyczna", "Karta sportowa", "PPK", "Elastyczne godziny"), "");
        JobOffer b2bUnpaid = offer("B2B bez urlopu", Seniority.MID, List.of(ContractType.B2B), 20000, 20000,
                List.of("Budżet szkoleniowy 6 000 zł"), "");
        JobOffer b2bPaid = offer("B2B z urlopem", Seniority.MID, List.of(ContractType.B2B), 20000, 20000, List.of(),
                "Oferujemy:\n- Kontrakt B2B, 26 dni płatnej przerwy");
        OfferService offers = mock(OfferService.class);
        for (JobOffer o : List.of(uop, b2bUnpaid, b2bPaid)) {
            when(offers.get(o.getId())).thenReturn(o);
        }
        OfferComparisonService service = new OfferComparisonService(offers, normalizer, calculator);
        OfferComparisonService.Comparison result = service.compare(new OfferComparisonService.CompareRequest(
                List.of(uop.getId(), b2bUnpaid.getId(), b2bPaid.getId()),
                Map.of(uop.getId(), new OfferComparisonService.PerOffer(300.0, 100.0, null, null)), null));

        assertThat(result.offers()).hasSize(3);
        assertThat(result.offers()).extracting(OfferComparisonService.ComparedOffer::rank).containsExactly(1, 2, 3);
        OfferComparisonService.ComparedOffer paid = find(result, b2bPaid);
        OfferComparisonService.ComparedOffer unpaid = find(result, b2bUnpaid);
        OfferComparisonService.ComparedOffer employment = find(result, uop);
        assertThat(paid.paidLeave()).isTrue();
        assertThat(unpaid.paidLeave()).isFalse();
        assertThat(unpaid.leaveCorrectionMonthly()).isGreaterThan(0);
        assertThat(unpaid.netMonthly()).isLessThan(paid.netMonthly());
        assertThat(unpaid.benefitsMonthly()).isEqualTo(500.0); // 6 000 zł rocznie ÷ 12
        assertThat(employment.benefits()).anyMatch(b -> b.matched() != null && b.matched().equals("PPK")
                && b.monthly() == 225.0);
        assertThat(employment.totalMonthly()).isEqualTo(Math.round((employment.netMonthly()
                + employment.benefitsMonthly() + 100 - 300) * 100) / 100.0);
        assertThat(employment.totalYearly()).isEqualTo(Math.round(employment.totalMonthly() * 12 * 100) / 100.0);
        assertThat(result.disclaimer()).contains("szacunek");
    }

    private static OfferComparisonService.ComparedOffer find(OfferComparisonService.Comparison c, JobOffer o) {
        return c.offers().stream().filter(x -> x.offerId().equals(o.getId())).findFirst().orElseThrow();
    }

    @Test
    void benefitCatalogParsesTrainingBudgetAndUnknownBenefits() {
        List<BenefitCatalog.BenefitValue> values = BenefitCatalog.value(
                List.of("Budżet szkoleniowy", "Owocowe czwartki", "MacBook"),
                "Budżet szkoleniowy 5 000 zł rocznie", null);
        assertThat(values.get(0).monthly()).isEqualTo(Math.round(5000 / 12.0 * 100) / 100.0);
        assertThat(values.get(1).matched()).isNull();
        assertThat(values.get(1).monthly()).isZero();
        assertThat(values.get(2).matched()).isEqualTo("Narzędzia pracy");
    }

    @Test
    void paidLeaveDetection() {
        JobOffer o = offer("X", Seniority.MID, List.of(ContractType.B2B), 1, 2, List.of(), "26 dni płatnej przerwy");
        assertThat(OfferPay.mentionsPaidLeave(o)).isTrue();
        o.setDescription("Paid time off: 24 days");
        assertThat(OfferPay.mentionsPaidLeave(o)).isTrue();
        o.setDescription("Elastyczne godziny pracy");
        assertThat(OfferPay.mentionsPaidLeave(o)).isFalse();
    }

    @Test
    void negotiationAnchorTakesMaximumAndConvertsToB2b() {
        NegotiationService service = new NegotiationService(null, normalizer, null, null, null, SalaryProperties.defaults());
        JobOffer b2b = offer("B2B", Seniority.SENIOR, List.of(ContractType.B2B), 22000, 28000, List.of(), "");
        b2b.setWorkMode(WorkMode.HYBRID);
        NormalizedSalary n = normalizer.normalize(b2b); // ≈ 17 600–22 400 zł UoP brutto
        SalaryStatsService.Market market = new SalaryStatsService.Market(
                SalaryStatsService.distribution(List.of(18000, 20000, 22000, 24000, 26000)), "oferty na poziomie senior", true);
        NegotiationService.NegotiationPlan plan = service.plan(b2b, n, market, 15000, null);
        // max(22 400, p75 = 24 000, 15 000 × 1,1 = 16 500) = 24 000
        assertThat(plan.anchor().comparable()).isEqualTo(24000);
        assertThat(plan.anchorComponents()).filteredOn(NegotiationService.AnchorComponent::decisive).singleElement()
                .satisfies(c -> assertThat(c.label()).contains("75. percentyl"));
        assertThat(plan.anchor().inOffer()).isEqualTo(30000); // × 1,25 → B2B netto
        assertThat(plan.anchor().text()).contains("netto + VAT");
        assertThat(plan.target().comparable()).isLessThanOrEqualTo(plan.anchor().comparable());
        assertThat(plan.counterOffer().comparable()).isBetween(plan.target().comparable(), plan.anchor().comparable());
        assertThat(plan.walkAway().comparable()).isEqualTo(15000);
        assertThat(plan.phrases()).anyMatch(p -> p.contains(plan.anchor().text()));
        assertThat(plan.levers()).anyMatch(l -> l.startsWith("Płatną przerwę"));
        assertThat(plan.avoid()).isNotEmpty();
    }

    @Test
    void negotiationWithoutAnyDataAsksForBudget() {
        NegotiationService service = new NegotiationService(null, normalizer, null, null, null, SalaryProperties.defaults());
        JobOffer none = offer("Bez widełek", null, List.of(ContractType.UOP), null, null, List.of(), "");
        NegotiationService.NegotiationPlan plan = service.plan(none, normalizer.normalize(none),
                new SalaryStatsService.Market(SalaryStatsService.distribution(List.of()), "wszystkie zapisane oferty", false),
                null, null);
        assertThat(plan.anchor()).isNull();
        assertThat(plan.phrases()).singleElement().satisfies(p -> assertThat(p).contains("budżet"));
    }

    @Test
    void hourlyOfferGetsHourlyAnchor() {
        NegotiationService service = new NegotiationService(null, normalizer, null, null, null, SalaryProperties.defaults());
        JobOffer hourly = offer("Godzinowa", Seniority.MID, List.of(ContractType.B2B), 140, 170, List.of(), "");
        hourly.setSalaryPeriod(SalaryPeriod.HOUR);
        NegotiationService.Amount a = service.amount(20000, hourly, normalizer.normalize(hourly));
        assertThat(a.inOffer()).isEqualTo(149); // 20 000 × 1,25 = 25 000 ÷ 168 h
        assertThat(a.text()).contains("zł/h");
    }
}

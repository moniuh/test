package pl.radarpracy.salary.tools;

import org.junit.jupiter.api.Test;
import pl.radarpracy.common.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NetSalaryCalculatorTest {

    private final NetSalaryCalculator calc = new NetSalaryCalculator();

    @Test
    void minimumWageOnEmploymentContractMatchesPublishedNet() {
        // płaca minimalna 2026: 4 806 zł brutto → ok. 3 605,85 zł netto (KUP 250 zł, PIT-2, bez PPK)
        NetResult r = calc.calculate(NetRequest.of(4806, ContractKind.UOP));
        assertThat(r.netMonthly()).isCloseTo(3605.85, within(0.01));
        assertThat(r.socialMonthly()).isCloseTo(658.91, within(0.01));
        assertThat(r.healthMonthly()).isCloseTo(373.24, within(0.01));
        assertThat(r.pitMonthly()).isCloseTo(168, within(0.01));
        assertThat(r.netYearly()).isCloseTo(3605.85 * 12, within(0.05));
        assertThat(r.netFirstMonth()).isEqualTo(r.netLastMonth());
        assertThat(r.disclaimer()).contains("szacunek");
        assertThat(r.items()).anyMatch(i -> i.kind().equals("RESULT") && i.label().equals("Na rękę"));
    }

    @Test
    void highSalaryCrossesThresholdAndPensionLimitDuringTheYear() {
        NetResult r = calc.calculate(NetRequest.of(30000, ContractKind.UOP));
        assertThat(r.assumptions()).anyMatch(a -> a.contains("próg")).anyMatch(a -> a.contains("30-krotności"));
        assertThat(r.netFirstMonth()).isNotEqualTo(r.netLastMonth());
        assertThat(r.netMonthly()).isBetween(15000.0, 25000.0);
    }

    @Test
    void raisedCostsAndPpkChangeTheNet() {
        double basic = calc.calculate(NetRequest.of(10000, ContractKind.UOP)).netMonthly();
        NetResult raised = calc.calculate(new NetRequest(10000.0, ContractKind.UOP, null, null, null, null,
                KupVariant.RAISED, null, null, null));
        NetResult ppk = calc.calculate(new NetRequest(10000.0, ContractKind.UOP, null, null, null, true, null, null,
                null, null));
        assertThat(raised.netMonthly()).isGreaterThan(basic);
        assertThat(ppk.netMonthly()).isLessThan(basic);
        assertThat(ppk.items()).anyMatch(i -> i.label().startsWith("PPK — wpłata pracownika") && i.monthly() == 200);
    }

    @Test
    void youthReliefRemovesPitBelowLimit() {
        NetResult r = calc.calculate(new NetRequest(6000.0, ContractKind.UOP, null, null, null, null, null, true,
                null, null));
        assertThat(r.pitMonthly()).isZero();
        assertThat(r.socialMonthly()).isGreaterThan(0);
        NetResult over = calc.calculate(new NetRequest(9000.0, ContractKind.UOP, null, null, null, null, null, true,
                null, null));
        assertThat(over.pitMonthly()).isGreaterThan(0);
        assertThat(over.assumptions()).anyMatch(a -> a.contains("Limit wyczerpany"));
    }

    @Test
    void studentOnCivilContractPaysNoContributions() {
        NetResult r = calc.calculate(new NetRequest(4000.0, ContractKind.UZ, null, null, null, null, null, null,
                true, null));
        assertThat(r.socialMonthly()).isZero();
        assertThat(r.healthMonthly()).isZero();
        assertThat(r.netMonthly()).isEqualTo(4000.0);
    }

    @Test
    void civilContractUsesTwentyPercentCosts() {
        NetResult r = calc.calculate(NetRequest.of(5000, ContractKind.UZ));
        // składki 13,71% (z chorobowym), zdrowotna 9%, KUP 20%
        assertThat(r.socialMonthly()).isCloseTo(685.50, within(0.01));
        assertThat(r.netMonthly()).isBetween(3500.0, 3900.0);
    }

    @Test
    void b2bLinearWithFullZus() {
        NetResult r = calc.calculate(NetRequest.of(20000, ContractKind.B2B_LINIOWY));
        // pełny ZUS 2026: 1 788,29 zł społecznych (z chorobowym) + 138,47 zł FP = 1 926,76 zł
        assertThat(r.socialMonthly()).isCloseTo(1788.29, within(0.01));
        assertThat(r.items()).anyMatch(i -> i.label().startsWith("Fundusz Pracy") && i.monthly() == 138.47);
        assertThat(r.healthMonthly()).isCloseTo(885.59, within(0.01));
        assertThat(r.netYearly()).isCloseTo(167063.80, within(0.01));
        assertThat(r.netFirstMonth()).isNull();
    }

    @Test
    void b2bRyczaltUsesRevenueBands() {
        NetResult r = calc.calculate(NetRequest.of(20000, ContractKind.B2B_RYCZALT));
        assertThat(r.healthMonthly()).isCloseTo(830.58, within(0.001));
        assertThat(r.pitMonthly() * 12).isCloseTo(25627, within(0.1));
        assertThat(r.netYearly()).isCloseTo(181284.92, within(0.01));
        NetResult low = calc.calculate(NetRequest.of(4000, ContractKind.B2B_RYCZALT));
        assertThat(low.healthMonthly()).isCloseTo(498.35, within(0.001));
        NetResult high = calc.calculate(NetRequest.of(30000, ContractKind.B2B_RYCZALT));
        assertThat(high.healthMonthly()).isCloseTo(1495.04, within(0.001));
    }

    @Test
    void b2bScaleHasMinimumHealthContribution() {
        NetResult r = calc.calculate(new NetRequest(3000.0, ContractKind.B2B_SKALA, null, ZusVariant.START_RELIEF,
                null, null, null, null, null, null));
        assertThat(r.socialMonthly()).isZero();
        assertThat(r.healthMonthly()).isCloseTo(TaxParameters2026.HEALTH_MIN_MONTHLY, within(0.001));
        // 36 000 zł dochodu rocznie: 12% × 36 000 − 3 600 = 720 zł podatku
        assertThat(r.pitMonthly() * 12).isCloseTo(720, within(0.01));
    }

    @Test
    void preferentialZusMatchesPublishedAmount() {
        NetResult r = calc.calculate(new NetRequest(10000.0, ContractKind.B2B_LINIOWY, null, ZusVariant.PREFERENTIAL,
                true, null, null, null, null, null));
        assertThat(r.socialMonthly()).isCloseTo(456.18, within(0.01));
        assertThat(r.otherMonthly()).isZero();
    }

    @Test
    void scaleTaxFollowsThresholdAndFreeAmount() {
        assertThat(NetSalaryCalculator.scaleTax(30000)).isZero();
        assertThat(NetSalaryCalculator.scaleTax(120000)).isEqualTo(10800);
        assertThat(NetSalaryCalculator.scaleTax(130000)).isEqualTo(14000);
        assertThat(NetSalaryCalculator.monthlyAdvance(0, 3897)).isEqualTo(168);
        assertThat(NetSalaryCalculator.monthlyAdvance(119000, 2000)).isEqualTo(Math.round(1000 * 0.12 + 1000 * 0.32 - 300));
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> calc.calculate(NetRequest.of(0, ContractKind.UOP)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> calc.calculate(new NetRequest(10000.0, null, null, null, null, null, null, null, null,
                null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> calc.calculate(new NetRequest(10000.0, ContractKind.B2B_RYCZALT, 13.0, null, null,
                null, null, null, null, null))).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("12%");
    }
}

package pl.radarpracy.salary;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryParserTest {

    private final SalaryNormalizer normalizer = new SalaryNormalizer(SalaryProperties.defaults());

    @Test
    void parsesMonthlyGross() {
        SalaryRange r = SalaryParser.parse("Umowa o pracę, 14 000 – 18 000 zł brutto miesięcznie").orElseThrow();
        assertThat(r.min()).isEqualTo(14000);
        assertThat(r.max()).isEqualTo(18000);
        assertThat(r.currency()).isEqualTo("PLN");
        assertThat(r.period()).isEqualTo(SalaryPeriod.MONTH);
        assertThat(r.gross()).isTrue();
        assertThat(r.contractHint()).isEqualTo(ContractType.UOP);
    }

    @Test
    void parsesHourlyB2bAndThousandsSuffix() {
        SalaryRange h = SalaryParser.parse("B2B: 140–170 zł/h netto + VAT").orElseThrow();
        assertThat(h.min()).isEqualTo(140);
        assertThat(h.period()).isEqualTo(SalaryPeriod.HOUR);
        assertThat(h.contractHint()).isEqualTo(ContractType.B2B);
        assertThat(h.gross()).isTrue();

        SalaryRange k = SalaryParser.parse("18k-24k PLN net").orElseThrow();
        assertThat(k.min()).isEqualTo(18000);
        assertThat(k.max()).isEqualTo(24000);
        assertThat(k.gross()).isFalse();
    }

    @Test
    void parsesUpperBoundForeignCurrencyAndYearly() {
        SalaryRange upTo = SalaryParser.parse("Wynagrodzenie do 25 000 zł").orElseThrow();
        assertThat(upTo.min()).isNull();
        assertThat(upTo.max()).isEqualTo(25000);

        SalaryRange eur = SalaryParser.parse("B2B contract: 5 500 – 6 500 EUR per month").orElseThrow();
        assertThat(eur.currency()).isEqualTo("EUR");
        assertThat(eur.min()).isEqualTo(5500);

        SalaryRange year = SalaryParser.parse("120 000 – 150 000 PLN rocznie").orElseThrow();
        assertThat(year.period()).isEqualTo(SalaryPeriod.YEAR);
    }

    @Test
    void ignoresNumbersThatAreNotMoney() {
        assertThat(SalaryParser.parse("15 zleceń miesięcznie, 3 lata doświadczenia")).isEmpty();
    }

    @Test
    void normalizesToMonthlyAndComparable() {
        NormalizedSalary hourly = normalizer.normalize(140, 170, "PLN", SalaryPeriod.HOUR, true, List.of(ContractType.B2B));
        assertThat(hourly.monthlyMin()).isEqualTo(23520);
        assertThat(hourly.basis()).isEqualTo("B2B_NET");
        assertThat(hourly.comparableMin()).isEqualTo(18820);

        NormalizedSalary eur = normalizer.normalize(5500, null, "EUR", SalaryPeriod.MONTH, true, List.of(ContractType.UOP));
        assertThat(eur.monthlyMin()).isEqualTo(23380);

        NormalizedSalary net = normalizer.normalize(10000, null, "PLN", SalaryPeriod.MONTH, false, List.of());
        assertThat(net.comparableMin()).isEqualTo(14000);

        assertThat(normalizer.normalize(null, null, null, null, null, List.of()).present()).isFalse();
    }
}

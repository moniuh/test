package pl.radarpracy.salary;

import org.springframework.stereotype.Service;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprowadza widełki do miesięcznej kwoty w PLN i przybliżonego równoważnika UoP brutto (pomysł #17).
 *
 * <p>Współczynniki są celowo proste i jawne (konfiguracja {@code radar.salary}) — dokładne
 * wyliczenie „na rękę” robi kalkulator netto.</p>
 */
@Service
public class SalaryNormalizer {

    private final SalaryProperties props;

    public SalaryNormalizer(SalaryProperties props) {
        this.props = props;
    }

    public NormalizedSalary normalize(JobOffer o) {
        return normalize(o.getSalaryMin(), o.getSalaryMax(), o.getSalaryCurrency(), o.getSalaryPeriod(),
                o.getSalaryGross(), o.getContractTypes());
    }

    public NormalizedSalary normalize(SalaryRange r) {
        List<ContractType> contracts = r.contractHint() == null ? List.of() : List.of(r.contractHint());
        return normalize(r.min(), r.max(), r.currency(), r.period(), r.gross(), contracts);
    }

    public NormalizedSalary normalize(Integer min, Integer max, String currency, SalaryPeriod period, Boolean gross,
                                      List<ContractType> contracts) {
        if (min == null && max == null) {
            return NormalizedSalary.absent();
        }
        List<String> steps = new ArrayList<>();
        SalaryPeriod p = period == null ? SalaryPeriod.MONTH : period;
        double periodFactor = switch (p) {
            case HOUR -> props.hoursPerMonth();
            case DAY -> props.daysPerMonth();
            case MONTH -> 1.0;
            case YEAR -> 1.0 / 12.0;
        };
        switch (p) {
            case HOUR -> steps.add("stawka godzinowa × " + props.hoursPerMonth() + " h");
            case DAY -> steps.add("stawka dzienna × " + props.daysPerMonth() + " dni");
            case YEAR -> steps.add("kwota roczna ÷ 12");
            default -> {
            }
        }
        String cur = currency == null ? "PLN" : currency.toUpperCase();
        double fx = 1.0;
        if (!"PLN".equals(cur)) {
            Double rate = props.fx().get(cur);
            if (rate == null) {
                return new NormalizedSalary(true, null, null, "UNKNOWN", null, null,
                        "Brak kursu dla waluty " + cur + " — dodaj go w radar.salary.fx");
            }
            fx = rate;
            steps.add("kurs " + cur + " = " + rate + " PLN");
        }
        Integer mMin = scale(min, periodFactor * fx);
        Integer mMax = scale(max, periodFactor * fx);

        boolean b2b = contracts != null && contracts.contains(ContractType.B2B) && !contracts.contains(ContractType.UOP);
        String basis;
        double toComparable;
        if (b2b) {
            basis = "B2B_NET";
            toComparable = 1.0 / props.b2bToUopFactor();
            steps.add("B2B ÷ " + props.b2bToUopFactor() + " ≈ równoważnik UoP brutto");
        } else if (Boolean.FALSE.equals(gross)) {
            basis = "NET";
            toComparable = props.netToGrossFactor();
            steps.add("netto × " + props.netToGrossFactor() + " ≈ brutto");
        } else if (Boolean.TRUE.equals(gross)) {
            basis = "UOP_GROSS";
            toComparable = 1.0;
        } else {
            basis = "UNKNOWN";
            toComparable = 1.0;
            steps.add("nie podano brutto/netto — przyjęto brutto");
        }
        Integer cMin = scale(mMin, toComparable);
        Integer cMax = scale(mMax, toComparable);
        String explanation = steps.isEmpty() ? "kwota miesięczna brutto w PLN" : String.join("; ", steps);
        return new NormalizedSalary(true, mMin, mMax, basis, cMin, cMax, explanation);
    }

    private static Integer scale(Integer v, double factor) {
        if (v == null) {
            return null;
        }
        return (int) (Math.round(v * factor / 10.0) * 10);
    }
}

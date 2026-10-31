package pl.radarpracy.salary.tools;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static pl.radarpracy.salary.tools.Fmt.pct;
import static pl.radarpracy.salary.tools.Fmt.pctValue;
import static pl.radarpracy.salary.tools.Fmt.round0;
import static pl.radarpracy.salary.tools.Fmt.round2;
import static pl.radarpracy.salary.tools.Fmt.zl;
import static pl.radarpracy.salary.tools.Fmt.zl2;
import static pl.radarpracy.salary.tools.TaxParameters2026.*;

/**
 * Kalkulator „na rękę” dla UoP, zlecenia i B2B (pomysł #63) — deterministyczny szacunek na rok 2026.
 *
 * <p>UoP i zlecenie liczymy miesiąc po miesiącu przez cały rok (jak lista płac), dzięki czemu
 * uwzględniamy przekroczenie progu 120 000 zł, limit 30-krotności i limit ulgi dla młodych w trakcie roku.
 * B2B liczymy rocznie (stały przychód co miesiąc), wynik miesięczny to średnia.</p>
 */
@Component
public class NetSalaryCalculator {

    public static final String DISCLAIMER = "To szacunek na podstawie parametrów na " + YEAR
            + " r., a nie porada podatkowa. Rzeczywista kwota zależy od Twojej sytuacji (inne przychody, ulgi, "
            + "wspólne rozliczenie, terminy wypłat) — w razie wątpliwości zapytaj księgową lub doradcę podatkowego.";

    private static final String[] MONTHS = {"styczniu", "lutym", "marcu", "kwietniu", "maju", "czerwcu", "lipcu",
            "sierpniu", "wrześniu", "październiku", "listopadzie", "grudniu"};

    public NetResult calculate(NetRequest r) {
        if (r == null || r.contract() == null) {
            throw new BadRequestException("Wybierz formę umowy");
        }
        if (r.amount() == null || r.amount() <= 0) {
            throw new BadRequestException("Podaj kwotę miesięczną większą od zera");
        }
        if (r.amount() > 10_000_000) {
            throw new BadRequestException("Kwota jest nierealnie wysoka — podaj kwotę miesięczną");
        }
        if (r.costsMonthly() != null && r.costsMonthly() < 0) {
            throw new BadRequestException("Koszty działalności nie mogą być ujemne");
        }
        return switch (r.contract()) {
            case UOP -> employee(r, false);
            case UZ -> employee(r, true);
            case B2B_LINIOWY, B2B_RYCZALT, B2B_SKALA -> business(r);
        };
    }

    // ------------------------------------------------------------------ UoP i zlecenie

    private NetResult employee(NetRequest r, boolean civil) {
        double gross = round2(r.amount());
        boolean student = civil && Boolean.TRUE.equals(r.student());
        boolean under26 = Boolean.TRUE.equals(r.under26()) || student;
        boolean ppk = Boolean.TRUE.equals(r.ppk()) && !student;
        boolean sickness = !civil || !Boolean.FALSE.equals(r.sickness());
        double kupMonthly = r.kup() == KupVariant.RAISED ? KUP_RAISED_MONTHLY : KUP_BASIC_MONTHLY;

        double cumPensionBase = 0;
        double cumRevenue = 0;
        double cumTaxBase = 0;
        double sumPension = 0;
        double sumDisability = 0;
        double sumSickness = 0;
        double sumHealth = 0;
        double sumTax = 0;
        double sumPpkEmployee = 0;
        double sumPpkEmployer = 0;
        double sumExempt = 0;
        double sumNet = 0;
        double firstNet = 0;
        double lastNet = 0;
        int thresholdMonth = -1;
        int limitMonth = -1;
        int youthLimitMonth = -1;

        for (int m = 0; m < 12; m++) {
            double pensionBase = student ? 0 : Math.min(gross, Math.max(0, PENSION_BASE_LIMIT - cumPensionBase));
            if (!student && pensionBase < gross && limitMonth < 0) {
                limitMonth = m;
            }
            cumPensionBase += pensionBase;
            double pension = round2(pensionBase * EMPLOYEE_PENSION_RATE);
            double disability = round2(pensionBase * EMPLOYEE_DISABILITY_RATE);
            double sick = student || !sickness ? 0 : round2(gross * SICKNESS_RATE);
            double social = pension + disability + sick;
            double health = student ? 0 : round2((gross - social) * HEALTH_RATE);
            double ppkEmployee = ppk ? round2(gross * PPK_EMPLOYEE_RATE) : 0;
            double ppkEmployer = ppk ? round2(gross * PPK_EMPLOYER_RATE) : 0;

            double revenue = gross + ppkEmployer;
            double exempt = under26 ? Math.min(revenue, Math.max(0, YOUTH_RELIEF_LIMIT - cumRevenue)) : 0;
            if (under26 && exempt < revenue && youthLimitMonth < 0) {
                youthLimitMonth = m;
            }
            cumRevenue += revenue;
            double taxableRevenue = revenue - exempt;
            double tax = 0;
            if (taxableRevenue > 0) {
                // składki od przychodu zwolnionego nie obniżają podstawy — odliczamy proporcjonalnie
                double socialDeductible = social * taxableRevenue / revenue;
                double kup = civil ? KUP_CIVIL_RATE * (taxableRevenue - socialDeductible)
                        : Math.min(kupMonthly, taxableRevenue);
                double base = Math.max(0, round0(taxableRevenue - socialDeductible - kup));
                if (cumTaxBase + base > PIT_THRESHOLD && thresholdMonth < 0) {
                    thresholdMonth = m;
                }
                tax = monthlyAdvance(cumTaxBase, base);
                cumTaxBase += base;
            }
            double net = round2(gross - social - health - tax - ppkEmployee);
            if (m == 0) {
                firstNet = net;
            }
            lastNet = net;
            sumPension += pension;
            sumDisability += disability;
            sumSickness += sick;
            sumHealth += health;
            sumTax += tax;
            sumPpkEmployee += ppkEmployee;
            sumPpkEmployer += ppkEmployer;
            sumExempt += exempt;
            sumNet += net;
        }

        List<NetResult.NetItem> items = new ArrayList<>();
        items.add(item(civil ? "Wynagrodzenie brutto (zlecenie)" : "Wynagrodzenie brutto", gross * 12, "INCOME"));
        if (!student) {
            items.add(item("Składka emerytalna (" + pct(EMPLOYEE_PENSION_RATE) + ")", sumPension, "DEDUCTION"));
            items.add(item("Składka rentowa (" + pct(EMPLOYEE_DISABILITY_RATE) + ")", sumDisability, "DEDUCTION"));
        }
        if (sumSickness > 0) {
            items.add(item("Składka chorobowa (" + pct(SICKNESS_RATE) + ")", sumSickness, "DEDUCTION"));
        }
        if (!student) {
            items.add(item("Składka zdrowotna (" + pct(HEALTH_RATE) + ")", sumHealth, "DEDUCTION"));
        }
        items.add(item(civil ? "Zaliczki na PIT" : "Zaliczki na PIT (skala 12%/32%)", sumTax, "DEDUCTION"));
        if (ppk) {
            items.add(item("PPK — wpłata pracownika (" + pct(PPK_EMPLOYEE_RATE) + ")", sumPpkEmployee, "DEDUCTION"));
            items.add(item("PPK — wpłata pracodawcy (" + pct(PPK_EMPLOYER_RATE) + ", opodatkowana, trafia na Twoje konto)",
                    sumPpkEmployer, "INFO"));
        }
        if (under26 && sumExempt > 0) {
            items.add(item("Przychód zwolniony z PIT (ulga dla młodych)", sumExempt, "INFO"));
        }
        items.add(item("Na rękę", sumNet, "RESULT"));

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Liczymy 12 miesięcy pracy w " + YEAR + " r. z tą samą kwotą brutto; kwoty miesięczne to średnia "
                + "z całego roku.");
        assumptions.add("Złożony PIT-2: zaliczka pomniejszana o " + zl(TAX_REDUCING_AMOUNT_MONTHLY)
                + " miesięcznie (kwota wolna " + zl(TAX_FREE_AMOUNT) + " rocznie).");
        if (civil) {
            assumptions.add(student
                    ? "Student lub uczeń do 26 lat na zleceniu: bez składek ZUS i zdrowotnej, przychód objęty ulgą dla młodych."
                    : "Zlecenie jako jedyny tytuł do ubezpieczeń: pełne składki emerytalna i rentowa, chorobowa "
                            + (sickness ? "dobrowolna — włączona" : "dobrowolna — wyłączona") + "; koszty uzyskania "
                            + pct(KUP_CIVIL_RATE) + ".");
            assumptions.add("Pomijamy zlecenia do 200 zł (podatek ryczałtowy) i minimalną stawkę godzinową.");
        } else {
            assumptions.add("Koszty uzyskania przychodu: " + zl(kupMonthly) + " miesięcznie ("
                    + (r.kup() == KupVariant.RAISED ? "podwyższone — dojazd z innej miejscowości" : "podstawowe") + ").");
        }
        if (thresholdMonth >= 0) {
            assumptions.add("Dochód przekracza próg " + zl(PIT_THRESHOLD) + " w " + MONTHS[thresholdMonth]
                    + " — od tego miesiąca zaliczka rośnie do " + pct(PIT_RATE_2) + ", więc netto spada.");
        }
        if (limitMonth >= 0) {
            assumptions.add("Limit 30-krotności (" + zl(PENSION_BASE_LIMIT) + ") osiągnięty w " + MONTHS[limitMonth]
                    + " — potem nie płacisz składki emerytalnej i rentowej, więc netto rośnie.");
        }
        if (under26) {
            assumptions.add("Ulga dla młodych: przychody do " + zl(YOUTH_RELIEF_LIMIT) + " rocznie bez PIT"
                    + (student ? "." : "; składki ZUS i zdrowotna płacone normalnie.")
                    + (youthLimitMonth >= 0 ? " Limit wyczerpany w " + MONTHS[youthLimitMonth] + "." : ""));
        }
        if (ppk) {
            assumptions.add("PPK: wpłaty podstawowe 2% (Ty) i 1,5% (pracodawca); wpłata pracodawcy zwiększa podstawę PIT.");
        }
        assumptions.add("Roczne zeznanie może dać niewielki zwrot lub dopłatę (zaokrąglenia, inne przychody, ulgi, "
                + "wspólne rozliczenie) — nie są uwzględnione. Pomijamy premie, nadgodziny i świadczenia w naturze "
                + "(np. opieka medyczna opłacana przez pracodawcę zwiększa podstawę PIT).");

        return new NetResult(YEAR, r.contract(), r.contract().label(), gross, items,
                round2((sumPension + sumDisability + sumSickness) / 12), round2(sumHealth / 12), round2(sumTax / 12),
                round2(sumPpkEmployee / 12), round2(sumNet / 12), round2(sumNet), firstNet, lastNet, assumptions,
                List.of(), DISCLAIMER);
    }

    /** Zaliczka miesięczna na skali: 12% do progu, 32% nadwyżki, minus 300 zł (PIT-2); w pełnych złotych. */
    static double monthlyAdvance(double cumulativeBefore, double base) {
        double below = Math.max(0, Math.min(base, PIT_THRESHOLD - cumulativeBefore));
        double above = base - below;
        double tax = below * PIT_RATE_1 + above * PIT_RATE_2 - TAX_REDUCING_AMOUNT_MONTHLY;
        return Math.max(0, round0(tax));
    }

    /** Podatek roczny według skali (z kwotą wolną 30 000 zł). */
    static double scaleTax(double annualBase) {
        if (annualBase <= 0) {
            return 0;
        }
        double tax = annualBase <= PIT_THRESHOLD
                ? annualBase * PIT_RATE_1 - TAX_REDUCING_AMOUNT_YEARLY
                : PIT_THRESHOLD * PIT_RATE_1 - TAX_REDUCING_AMOUNT_YEARLY + (annualBase - PIT_THRESHOLD) * PIT_RATE_2;
        return Math.max(0, round0(tax));
    }

    // ------------------------------------------------------------------ B2B

    private NetResult business(NetRequest r) {
        ContractKind c = r.contract();
        double revenueM = round2(r.amount());
        double costsM = r.costsMonthly() == null ? 0 : round2(r.costsMonthly());
        ZusVariant zus = r.zus() == null ? ZusVariant.FULL : r.zus();
        boolean sickness = !Boolean.FALSE.equals(r.sickness());
        double rate = r.ryczaltRate() == null ? RYCZALT_DEFAULT_RATE : r.ryczaltRate();
        if (c == ContractKind.B2B_RYCZALT && !RYCZALT_RATES.contains(rate)) {
            throw new BadRequestException("Niedozwolona stawka ryczałtu: " + pctValue(rate) + ". Dozwolone: "
                    + String.join(", ", new TreeSet<>(RYCZALT_RATES).stream().map(Fmt::pctValue).toList()));
        }

        double zusBase = switch (zus) {
            case FULL -> ZUS_FULL_BASE;
            case PREFERENTIAL -> ZUS_PREFERENTIAL_BASE;
            case START_RELIEF -> 0;
        };
        double pensionM = round2(zusBase * BUSINESS_PENSION_RATE);
        double disabilityM = round2(zusBase * BUSINESS_DISABILITY_RATE);
        double accidentM = round2(zusBase * ACCIDENT_RATE);
        double sickM = sickness ? round2(zusBase * SICKNESS_RATE) : 0;
        double labourFundM = zus == ZusVariant.FULL ? round2(zusBase * LABOUR_FUND_RATE) : 0;
        double socialM = pensionM + disabilityM + accidentM + sickM;

        double revenue = revenueM * 12;
        double costs = costsM * 12;
        double social = socialM * 12;
        double labourFund = labourFundM * 12;
        double health;
        double tax;
        String taxLabel;
        String healthLabel;
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Kwota to miesięczny przychód netto z faktury (bez VAT — VAT jest neutralny i go pomijamy).");
        assumptions.add("Liczymy rocznie: 12 miesięcy z tym samym przychodem; kwoty miesięczne to średnia. "
                + "Zaliczki płacone w trakcie roku mogą się różnić od średniej.");
        assumptions.add(switch (zus) {
            case FULL -> "Pełny ZUS: podstawa " + zl2(ZUS_FULL_BASE) + " (60% prognozowanego przeciętnego wynagrodzenia "
                    + zl(FORECAST_AVERAGE_WAGE) + ") plus Fundusz Pracy " + pct(LABOUR_FUND_RATE) + ".";
            case PREFERENTIAL -> "Preferencyjny ZUS (pierwsze 24 miesiące działalności): podstawa "
                    + zl2(ZUS_PREFERENTIAL_BASE) + " (30% płacy minimalnej " + zl(MINIMUM_WAGE) + "), bez Funduszu Pracy.";
            case START_RELIEF -> "Ulga na start: bez składek społecznych — przysługuje tylko przez pierwsze 6 miesięcy, "
                    + "a kalkulator zakłada ją przez cały rok.";
        });
        if (zus != ZusVariant.START_RELIEF) {
            assumptions.add("Chorobowe dobrowolne: " + (sickness ? "włączone." : "wyłączone — nie masz prawa do zasiłku chorobowego."));
        }

        if (c == ContractKind.B2B_RYCZALT) {
            double healthM = revenue <= RYCZALT_THRESHOLD_1 ? RYCZALT_HEALTH_LOW
                    : revenue <= RYCZALT_THRESHOLD_2 ? RYCZALT_HEALTH_MID : RYCZALT_HEALTH_HIGH;
            health = healthM * 12;
            double base = Math.max(0, round0(revenue - social - RYCZALT_HEALTH_DEDUCTIBLE_SHARE * health));
            tax = round0(base * rate / 100);
            taxLabel = "Ryczałt (" + pctValue(rate) + " przychodu)";
            healthLabel = "Składka zdrowotna (ryczałt, " + zl2(healthM) + " miesięcznie)";
            assumptions.add("Ryczałt " + pctValue(rate) + " od przychodu pomniejszonego o składki społeczne i 50% "
                    + "zapłaconej zdrowotnej; koszty działalności nie obniżają podatku.");
            assumptions.add("Zdrowotna na ryczałcie zależy od rocznego przychodu: do " + zl(RYCZALT_THRESHOLD_1) + " — "
                    + zl2(RYCZALT_HEALTH_LOW) + ", do " + zl(RYCZALT_THRESHOLD_2) + " — " + zl2(RYCZALT_HEALTH_MID)
                    + ", powyżej — " + zl2(RYCZALT_HEALTH_HIGH) + " miesięcznie.");
        } else {
            double income = Math.max(0, revenue - costs - labourFund - social);
            if (c == ContractKind.B2B_LINIOWY) {
                double healthM = Math.max(HEALTH_MIN_MONTHLY, round2(income / 12 * HEALTH_LINEAR_RATE));
                health = healthM * 12;
                double deduction = Math.min(health, HEALTH_LINEAR_DEDUCTION_LIMIT);
                double base = Math.max(0, round0(income - deduction));
                tax = round0(base * LINEAR_RATE);
                taxLabel = "Podatek liniowy (" + pct(LINEAR_RATE) + ")";
                healthLabel = "Składka zdrowotna (" + pct(HEALTH_LINEAR_RATE) + " dochodu)";
                assumptions.add("Podatek liniowy " + pct(LINEAR_RATE) + " od dochodu (przychód − koszty − składki "
                        + "społeczne − odliczona zdrowotna, maks. " + zl(HEALTH_LINEAR_DEDUCTION_LIMIT) + " rocznie).");
                assumptions.add("Zdrowotna " + pct(HEALTH_LINEAR_RATE) + " dochodu, nie mniej niż "
                        + zl2(HEALTH_MIN_MONTHLY) + " miesięcznie.");
            } else {
                double healthM = Math.max(HEALTH_MIN_MONTHLY, round2(income / 12 * HEALTH_RATE));
                health = healthM * 12;
                tax = scaleTax(round0(income));
                taxLabel = "Podatek wg skali (" + pct(PIT_RATE_1) + "/" + pct(PIT_RATE_2) + ")";
                healthLabel = "Składka zdrowotna (" + pct(HEALTH_RATE) + " dochodu)";
                assumptions.add("Skala " + pct(PIT_RATE_1) + "/" + pct(PIT_RATE_2) + " z kwotą wolną " + zl(TAX_FREE_AMOUNT)
                        + " i progiem " + zl(PIT_THRESHOLD) + "; zdrowotna " + pct(HEALTH_RATE) + " dochodu (min. "
                        + zl2(HEALTH_MIN_MONTHLY) + "), bez odliczenia od podatku.");
            }
        }
        if (Boolean.TRUE.equals(r.under26())) {
            assumptions.add("Ulga dla młodych nie obejmuje działalności gospodarczej — pominięto ją.");
        }
        if (Boolean.TRUE.equals(r.ppk())) {
            assumptions.add("PPK nie dotyczy działalności gospodarczej — pominięto.");
        }
        assumptions.add("Brak płatnego urlopu i zasiłku od pracodawcy: każdy dzień wolny obniża przychód (porównywarka "
                + "ofert uwzględnia to korektą).");
        assumptions.add("Pomijamy Mały ZUS Plus, IP Box, ulgę B+R, wspólne rozliczenie z małżonkiem (możliwe tylko na "
                + "skali) i zdrowotną za styczeń " + YEAR + " liczoną jeszcze według stawek z poprzedniego roku.");

        double net = revenue - costs - social - labourFund - health - tax;
        List<NetResult.NetItem> items = new ArrayList<>();
        items.add(item("Przychód netto (bez VAT)", revenue, "INCOME"));
        if (costs > 0) {
            items.add(item("Koszty działalności", costs, "DEDUCTION"));
        }
        if (zusBase > 0) {
            items.add(item("Składka emerytalna (" + pct(BUSINESS_PENSION_RATE) + ")", pensionM * 12, "DEDUCTION"));
            items.add(item("Składka rentowa (" + pct(BUSINESS_DISABILITY_RATE) + ")", disabilityM * 12, "DEDUCTION"));
            items.add(item("Składka wypadkowa (" + pct(ACCIDENT_RATE) + ")", accidentM * 12, "DEDUCTION"));
            if (sickM > 0) {
                items.add(item("Składka chorobowa (" + pct(SICKNESS_RATE) + ", dobrowolna)", sickM * 12, "DEDUCTION"));
            }
            if (labourFundM > 0) {
                items.add(item("Fundusz Pracy (" + pct(LABOUR_FUND_RATE) + ")", labourFund, "DEDUCTION"));
            }
        }
        items.add(item(healthLabel, health, "DEDUCTION"));
        items.add(item(taxLabel, tax, "DEDUCTION"));
        items.add(item("Na rękę", net, "RESULT"));
        if (zusBase > 0) {
            items.add(new NetResult.NetItem("Podstawa składek społecznych", zusBase, zusBase * 12, "INFO"));
        }

        return new NetResult(YEAR, c, c.label(), revenueM, items, round2(social / 12), round2(health / 12),
                round2(tax / 12), round2((costs + labourFund) / 12), round2(net / 12), round2(net), null, null,
                assumptions, List.of(ESTIMATES.get(2)), DISCLAIMER);
    }

    private static NetResult.NetItem item(String label, double yearly, String kind) {
        return new NetResult.NetItem(label, round2(yearly / 12), round2(yearly), kind);
    }
}

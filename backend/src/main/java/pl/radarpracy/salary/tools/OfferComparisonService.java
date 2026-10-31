package pl.radarpracy.salary.tools;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.radarpracy.salary.tools.Fmt.round2;

/**
 * Porównywarka ofert (pomysł #64): netto, benefity, płatny urlop i dojazd sprowadzone do jednej wartości
 * miesięcznej i rocznej, z rankingiem. Liczy kod — deterministycznie i z opisem każdego kroku.
 */
@Service
public class OfferComparisonService {

    public static final int MAX_OFFERS = 6;

    /**
     * Ustawienia kandydata dla jednej oferty.
     *
     * @param commuteMonthly       miesięczny koszt dojazdu (zł)
     * @param extraBenefitsMonthly wartość innych benefitów, których tabela nie wycenia (zł/mies.)
     * @param contract             wymuszona forma rozliczenia B2B (liniowy / ryczałt / skala)
     * @param ryczaltRate          stawka ryczałtu w %
     */
    public record PerOffer(Double commuteMonthly, Double extraBenefitsMonthly, ContractKind contract,
                           Double ryczaltRate) {
    }

    /**
     * Żądanie porównania.
     *
     * @param offerIds   oferty do porównania (2–4 w interfejsie)
     * @param perOffer   ustawienia per oferta (klucz: id oferty)
     * @param b2bVariant domyślna forma B2B (domyślnie liniowy)
     */
    public record CompareRequest(List<Long> offerIds, Map<Long, PerOffer> perOffer, ContractKind b2bVariant) {
    }

    /** Wynik dla jednej oferty. */
    public record ComparedOffer(Long offerId, String title, String company, boolean salaryPresent,
                                ContractKind contract, String contractLabel, String basis, String basisLabel,
                                Integer monthlyAmount, Integer comparableMonthly, Double netMonthly,
                                boolean paidLeave, double leaveCorrectionMonthly,
                                List<BenefitCatalog.BenefitValue> benefits, double benefitsMonthly,
                                double extraBenefitsMonthly, double commuteMonthly, Double totalMonthly,
                                Double totalYearly, Integer rank, List<String> notes) {
    }

    /** Odpowiedź porównywarki. */
    public record Comparison(List<ComparedOffer> offers, List<String> assumptions, List<String> estimates,
                             String disclaimer) {
    }

    private final OfferService offers;
    private final SalaryNormalizer normalizer;
    private final NetSalaryCalculator calculator;

    public OfferComparisonService(OfferService offers, SalaryNormalizer normalizer, NetSalaryCalculator calculator) {
        this.offers = offers;
        this.normalizer = normalizer;
        this.calculator = calculator;
    }

    public Comparison compare(CompareRequest request) {
        if (request == null || request.offerIds() == null || request.offerIds().isEmpty()) {
            throw new BadRequestException("Wybierz oferty do porównania");
        }
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.offerIds()));
        if (ids.size() > MAX_OFFERS) {
            throw new BadRequestException("Porównać można najwyżej " + MAX_OFFERS + " ofert naraz");
        }
        Map<Long, PerOffer> settings = request.perOffer() == null ? Map.of() : request.perOffer();
        List<ComparedOffer> rows = new ArrayList<>();
        for (Long id : ids) {
            rows.add(compareOne(offers.get(id), settings.get(id), request.b2bVariant()));
        }
        List<ComparedOffer> ranked = rows.stream().filter(r -> r.totalMonthly() != null)
                .sorted(Comparator.comparingDouble(ComparedOffer::totalMonthly).reversed()).toList();
        List<ComparedOffer> out = new ArrayList<>();
        for (ComparedOffer r : rows) {
            int idx = ranked.indexOf(r);
            out.add(idx < 0 ? r : withRank(r, idx + 1));
        }
        out.sort(Comparator.comparing((ComparedOffer r) -> r.rank() == null ? Integer.MAX_VALUE : r.rank()));

        List<String> assumptions = List.of(
                "Wartość miesięczna = szacunek netto + wycena benefitów + inne benefity − koszt dojazdu; rocznie × 12.",
                "Netto liczone kalkulatorem dla środka widełek z normalizacji (kwoty miesięczne w PLN).",
                "UoP: płatny urlop wliczony w pensję. B2B i zlecenie bez wzmianki o płatnej przerwie: przychód "
                        + "pomniejszony o " + TaxParameters2026.PAID_LEAVE_DAYS + " dni nieodpłatnego urlopu ("
                        + TaxParameters2026.PAID_LEAVE_DAYS + "/" + TaxParameters2026.WORKING_DAYS_PER_YEAR
                        + " dni roboczych).",
                "Benefity w naturze na UoP są opodatkowane — wycena pokazuje ich wartość brutto dla Ciebie, bez podatku.");
        List<String> estimates = List.of(TaxParameters2026.ESTIMATES.get(0), TaxParameters2026.ESTIMATES.get(1));
        return new Comparison(out, assumptions, estimates, NetSalaryCalculator.DISCLAIMER);
    }

    ComparedOffer compareOne(JobOffer o, PerOffer s, ContractKind defaultB2b) {
        NormalizedSalary n = normalizer.normalize(o);
        double commute = s == null || s.commuteMonthly() == null ? 0 : Math.max(0, s.commuteMonthly());
        double extra = s == null || s.extraBenefitsMonthly() == null ? 0 : Math.max(0, s.extraBenefitsMonthly());
        ContractKind b2bVariant = s != null && s.contract() != null && s.contract().isB2b() ? s.contract() : defaultB2b;
        ContractKind contract = OfferPay.contractFor(o, n, b2bVariant);
        List<String> notes = new ArrayList<>();
        if (o.getContractTypes() == null || o.getContractTypes().isEmpty()) {
            notes.add("Oferta nie podaje formy umowy — przyjęto umowę o pracę.");
        }
        Integer mid = OfferPay.monthlyMid(n);
        boolean paidLeave = contract == ContractKind.UOP || OfferPay.mentionsPaidLeave(o);

        if (mid == null) {
            notes.add(n.present() ? n.explanation() : "Oferta nie podaje wynagrodzenia — nie da się policzyć netto.");
            List<BenefitCatalog.BenefitValue> benefits = BenefitCatalog.value(o.getBenefits(), o.getDescription(), null);
            return new ComparedOffer(o.getId(), o.getTitle(), o.getCompany(), false, contract, contract.label(),
                    n.basis(), OfferPay.basisLabel(n.basis()), null, null, null, paidLeave, 0, benefits,
                    sum(benefits), extra, commute, null, null, null, notes);
        }
        if (n.monthlyMin() == null || n.monthlyMax() == null) {
            notes.add("Oferta podaje tylko " + (n.monthlyMin() != null ? "dolną" : "górną") + " granicę — liczymy od niej.");
        }
        if (!"PLN".equalsIgnoreCase(o.getSalaryCurrency() == null ? "PLN" : o.getSalaryCurrency())) {
            notes.add("Kwota przeliczona z " + o.getSalaryCurrency() + " po kursie z konfiguracji.");
        }

        Double grossForPpk = contract == ContractKind.UOP || contract == ContractKind.UZ ? (double) mid : null;
        List<BenefitCatalog.BenefitValue> benefits = BenefitCatalog.value(o.getBenefits(), o.getDescription(),
                "NET".equals(n.basis()) ? null : grossForPpk);
        double benefitsMonthly = sum(benefits);

        double net;
        double leaveCorrection = 0;
        if ("NET".equals(n.basis())) {
            net = mid;
            notes.add("Oferta podaje kwotę netto — przyjmujemy ją wprost.");
        } else {
            NetRequest req = new NetRequest((double) mid, contract,
                    s != null && s.ryczaltRate() != null ? s.ryczaltRate() : null, null, null, false, null, null,
                    OfferPay.onlyWorkContract(o) ? Boolean.TRUE : null, null);
            if (OfferPay.onlyWorkContract(o)) {
                notes.add("Umowa o dzieło: szacunek jak dla zlecenia bez składek ZUS i zdrowotnej (KUP 20%).");
            }
            if ("UNKNOWN".equals(n.basis())) {
                notes.add("Oferta nie mówi, czy kwota jest brutto czy netto — przyjęto brutto.");
            }
            double fullNet = calculator.calculate(req).netMonthly();
            net = fullNet;
            if (!paidLeave) {
                double factor = 1.0 - (double) TaxParameters2026.PAID_LEAVE_DAYS / TaxParameters2026.WORKING_DAYS_PER_YEAR;
                net = calculator.calculate(req.withAmount(mid * factor)).netMonthly();
                leaveCorrection = round2(fullNet - net);
                notes.add("Brak płatnej przerwy: " + TaxParameters2026.PAID_LEAVE_DAYS
                        + " dni urlopu obniża przychód — korekta −" + Fmt.zl(leaveCorrection) + " netto miesięcznie.");
            } else if (contract.isB2b() || contract == ContractKind.UZ) {
                notes.add("Oferta wspomina płatną przerwę — bez korekty urlopowej.");
            }
        }
        double total = round2(net + benefitsMonthly + extra - commute);
        return new ComparedOffer(o.getId(), o.getTitle(), o.getCompany(), true, contract, contract.label(), n.basis(),
                OfferPay.basisLabel(n.basis()), mid, n.comparableMid(), round2(net), paidLeave, leaveCorrection,
                benefits, round2(benefitsMonthly), extra, commute, total, round2(total * 12), null, notes);
    }

    private static double sum(List<BenefitCatalog.BenefitValue> benefits) {
        return round2(benefits.stream().mapToDouble(BenefitCatalog.BenefitValue::monthly).sum());
    }

    private static ComparedOffer withRank(ComparedOffer r, int rank) {
        return new ComparedOffer(r.offerId(), r.title(), r.company(), r.salaryPresent(), r.contract(),
                r.contractLabel(), r.basis(), r.basisLabel(), r.monthlyAmount(), r.comparableMonthly(), r.netMonthly(),
                r.paidLeave(), r.leaveCorrectionMonthly(), r.benefits(), r.benefitsMonthly(), r.extraBenefitsMonthly(),
                r.commuteMonthly(), r.totalMonthly(), r.totalYearly(), rank, r.notes());
    }
}

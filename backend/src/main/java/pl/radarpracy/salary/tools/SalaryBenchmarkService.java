package pl.radarpracy.salary.tools;

import org.springframework.stereotype.Service;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;

import java.util.ArrayList;
import java.util.List;

import static pl.radarpracy.salary.tools.Fmt.zl;

/**
 * Ostrzeżenie o stawce poniżej rynku (pomysł #69) i szacunek netto dla panelu oferty.
 *
 * <p>Środek widełek oferty (równoważnik UoP brutto) porównujemy z medianą podobnych ofert: ten sam poziom,
 * a gdy takich ofert jest mniej niż 3 — wszystkie zapisane oferty. Poniżej 85% mediany ostrzegamy,
 * powyżej 115% informujemy.</p>
 */
@Service
public class SalaryBenchmarkService {

    static final double BELOW_RATIO = 0.85;
    static final double ABOVE_RATIO = 1.15;

    /** Werdykt porównania z rynkiem. */
    public enum Verdict { BELOW, IN_RANGE, ABOVE, NO_SALARY, NO_DATA }

    /** Odpowiedź {@code GET /api/offers/{id}/salary-benchmark}. */
    public record SalaryBenchmark(Long offerId, boolean salaryPresent, Integer offerMid, String peerGroup,
                                  boolean sameLevel, int peerCount, Integer median, Integer p25, Integer p75,
                                  Double ratio, Verdict verdict, String message, boolean smallSample,
                                  ContractKind netContract, Integer netAmount, NetResult net, String netNote,
                                  List<String> notes) {
    }

    private final OfferService offers;
    private final SalaryNormalizer normalizer;
    private final SalaryStatsService stats;
    private final NetSalaryCalculator calculator;

    public SalaryBenchmarkService(OfferService offers, SalaryNormalizer normalizer, SalaryStatsService stats,
                                  NetSalaryCalculator calculator) {
        this.offers = offers;
        this.normalizer = normalizer;
        this.stats = stats;
        this.calculator = calculator;
    }

    public SalaryBenchmark benchmark(Long offerId) {
        JobOffer o = offers.get(offerId);
        return benchmark(o, normalizer.normalize(o), stats.marketFor(o));
    }

    SalaryBenchmark benchmark(JobOffer o, NormalizedSalary n, SalaryStatsService.Market market) {
        List<String> notes = new ArrayList<>();
        SalaryStatsService.Distribution d = market.stats();
        Integer mid = n.present() ? n.comparableMid() : null;
        if (mid == null) {
            return new SalaryBenchmark(o.getId(), false, null, market.groupLabel(), market.sameLevel(), d.count(),
                    d.median(), d.p25(), d.p75(), null, Verdict.NO_SALARY,
                    n.present() ? n.explanation() : "Oferta nie podaje wynagrodzenia — zapytaj o widełki przed rozmową.",
                    d.smallSample(), null, null, null, null, notes);
        }

        Verdict verdict;
        Double ratio = null;
        String message;
        if (d.count() < 2 || d.median() == null || d.median() <= 0) {
            verdict = Verdict.NO_DATA;
            message = "Za mało ofert z widełkami, żeby porównać tę stawkę z rynkiem (" + d.count() + ").";
        } else {
            ratio = Math.round(1000.0 * mid / d.median()) / 1000.0;
            int pct = (int) Math.round(ratio * 100);
            if (ratio < BELOW_RATIO) {
                verdict = Verdict.BELOW;
                message = "Uwaga: środek widełek (" + zl(mid) + ") to " + pct + "% mediany — "
                        + market.groupLabel() + " (" + zl(d.median()) + "). Stawka poniżej rynku: warto negocjować.";
            } else if (ratio > ABOVE_RATIO) {
                verdict = Verdict.ABOVE;
                message = "Środek widełek (" + zl(mid) + ") to " + pct + "% mediany — " + market.groupLabel()
                        + " (" + zl(d.median()) + "). Stawka powyżej rynku.";
            } else {
                verdict = Verdict.IN_RANGE;
                message = "Środek widełek (" + zl(mid) + ") to " + pct + "% mediany — " + market.groupLabel()
                        + " (" + zl(d.median()) + "). Stawka w granicach rynku.";
            }
        }
        if (!market.sameLevel() && o.getSeniority() != null) {
            notes.add("Za mało ofert na tym samym poziomie — porównujemy ze wszystkimi zapisanymi ofertami.");
        }
        if (d.smallSample()) {
            notes.add("Mała próba (" + d.count() + " " + SalaryStatsService.ofert(d.count())
                    + ") — wynik orientacyjny.");
        }
        notes.add("Porównanie w równoważniku umowy o pracę brutto miesięcznie (" + n.explanation() + ").");

        ContractKind contract = OfferPay.contractFor(o, n, ContractKind.B2B_LINIOWY);
        Integer amount = OfferPay.monthlyMid(n);
        NetResult net = null;
        String netNote;
        if ("NET".equals(n.basis())) {
            netNote = "Oferta podaje kwotę netto: około " + zl(amount) + " miesięcznie.";
        } else {
            boolean workContract = OfferPay.onlyWorkContract(o);
            net = calculator.calculate(new NetRequest((double) amount, contract, null, null, null, null, null, null,
                    workContract ? Boolean.TRUE : null, null));
            netNote = "Szacunek dla środka widełek (" + zl(amount) + " " + OfferPay.basisLabel(n.basis()) + "), "
                    + contract.label() + (contract.isB2b() ? ", pełny ZUS z chorobowym" : "")
                    + (workContract ? "; umowa o dzieło liczona jak zlecenie bez składek" : "")
                    + (o.getContractTypes() == null || o.getContractTypes().isEmpty()
                    ? "; oferta nie podaje formy umowy — przyjęto umowę o pracę" : "") + ".";
        }
        return new SalaryBenchmark(o.getId(), true, mid, market.groupLabel(), market.sameLevel(), d.count(),
                d.median(), d.p25(), d.p75(), ratio, verdict, message, d.smallSample(), contract, amount, net, netNote,
                notes);
    }
}

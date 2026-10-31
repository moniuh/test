package pl.radarpracy.salary.tools;

import org.springframework.stereotype.Service;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.analysis.ScamAssessment;
import pl.radarpracy.offer.analysis.ScamDetector;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Statystyki widełek z zebranych ofert (pomysł #65) i grupa porównawcza dla ostrzeżeń (#69) i negocjacji (#66).
 *
 * <p>Wartość oferty to środek widełek w przybliżonym równoważniku UoP brutto miesięcznie
 * ({@link NormalizedSalary#comparableMid()}), więc można porównywać oferty z różnymi umowami.
 * Duplikaty (oznaczone {@code duplicateOfId}) są pomijane.</p>
 */
@Service
public class SalaryStatsService {

    /** Poniżej tej liczby ofert próba jest „mała” i UI to wyraźnie mówi. */
    public static final int SMALL_SAMPLE = 5;
    /** Minimalna liczba ofert z tego samego poziomu, by porównywać tylko z nimi. */
    public static final int MIN_SAME_LEVEL_PEERS = 3;

    /**
     * Rozkład wartości (zł/mies., równoważnik UoP brutto). Pola liczbowe są null przy pustej próbie.
     */
    public record Distribution(int count, Integer median, Integer p25, Integer p75, Integer min, Integer max,
                               boolean smallSample) {
    }

    /** Grupa (poziom lub forma umowy) z rozkładem. */
    public record Group(String key, String label, Distribution stats) {
    }

    /** Oferta w próbie — dla przejrzystości statystyk. */
    public record Sample(Long offerId, String title, String company, Seniority seniority, List<ContractType> contracts,
                         int comparableMid) {
    }

    /** Odpowiedź {@code GET /api/salary/stats}. */
    public record SalaryStats(Seniority seniority, ContractType contract, String basis, Distribution overall,
                              List<Group> bySeniority, List<Group> byContract, List<Sample> offers,
                              List<String> notes) {
    }

    /** Grupa porównawcza dla jednej oferty. */
    public record Market(Distribution stats, String groupLabel, boolean sameLevel) {
    }

    private final OfferService offers;
    private final SalaryNormalizer normalizer;

    public SalaryStatsService(OfferService offers, SalaryNormalizer normalizer) {
        this.offers = offers;
        this.normalizer = normalizer;
    }

    public SalaryStats stats(Seniority seniority, ContractType contract) {
        List<Sample> all = samples(offers.all());
        List<Sample> filtered = all.stream()
                .filter(s -> seniority == null || s.seniority() == seniority)
                .filter(s -> contract == null || s.contracts().contains(contract))
                .toList();
        List<Group> bySeniority = new ArrayList<>();
        for (Seniority level : Seniority.values()) {
            List<Integer> values = filtered.stream().filter(s -> s.seniority() == level)
                    .map(Sample::comparableMid).toList();
            if (!values.isEmpty()) {
                bySeniority.add(new Group(level.name(), seniorityLabel(level), distribution(values)));
            }
        }
        List<Integer> noLevel = filtered.stream().filter(s -> s.seniority() == null).map(Sample::comparableMid).toList();
        if (!noLevel.isEmpty()) {
            bySeniority.add(new Group("NONE", "Nie podano poziomu", distribution(noLevel)));
        }
        List<Group> byContract = new ArrayList<>();
        for (ContractType type : ContractType.values()) {
            List<Integer> values = filtered.stream().filter(s -> s.contracts().contains(type))
                    .map(Sample::comparableMid).toList();
            if (!values.isEmpty()) {
                byContract.add(new Group(type.name(), contractLabel(type), distribution(values)));
            }
        }
        List<Integer> noContract = filtered.stream().filter(s -> s.contracts().isEmpty())
                .map(Sample::comparableMid).toList();
        if (!noContract.isEmpty()) {
            byContract.add(new Group("NONE", "Nie podano umowy", distribution(noContract)));
        }

        Distribution overall = distribution(filtered.stream().map(Sample::comparableMid).toList());
        List<String> notes = new ArrayList<>();
        notes.add("Kwoty to środek widełek w przybliżonym równoważniku umowy o pracę brutto miesięcznie "
                + "(B2B ÷ współczynnik, netto × współczynnik z konfiguracji) — dzięki temu porównujemy różne umowy.");
        notes.add("Próba: " + overall.count() + " " + ofert(overall.count())
                + " z widełkami spośród zapisanych w aplikacji (bez duplikatów i ofert bez wynagrodzenia).");
        if (overall.smallSample()) {
            notes.add("Mała próba (mniej niż " + SMALL_SAMPLE + " ofert) — traktuj wynik wyłącznie orientacyjnie.");
        }
        notes.add("Oferta z kilkoma formami umowy (np. UoP lub B2B) liczy się w każdej z tych grup.");
        return new SalaryStats(seniority, contract, "UOP_GROSS_EQUIVALENT", overall, bySeniority, byContract, filtered,
                notes);
    }

    /** Grupa porównawcza: ten sam poziom (gdy co najmniej 3 oferty), w przeciwnym razie wszystkie oferty. */
    public Market marketFor(JobOffer offer) {
        Set<Long> excluded = new HashSet<>();
        excluded.add(offer.getId());
        if (offer.getDuplicateOfId() != null) {
            excluded.add(offer.getDuplicateOfId());
        }
        List<JobOffer> others = offers.all().stream()
                .filter(o -> !excluded.contains(o.getId()))
                .filter(o -> offer.getId() == null || !Objects.equals(o.getDuplicateOfId(), offer.getId()))
                .toList();
        List<Sample> samples = samples(others);
        if (offer.getSeniority() != null) {
            List<Integer> same = samples.stream().filter(s -> s.seniority() == offer.getSeniority())
                    .map(Sample::comparableMid).toList();
            if (same.size() >= MIN_SAME_LEVEL_PEERS) {
                return new Market(distribution(same), "oferty na poziomie " + seniorityLabel(offer.getSeniority()), true);
            }
        }
        return new Market(distribution(samples.stream().map(Sample::comparableMid).toList()),
                "wszystkie zapisane oferty", false);
    }

    List<Sample> samples(Collection<JobOffer> source) {
        List<Sample> out = new ArrayList<>();
        for (JobOffer o : source) {
            if (o.getDuplicateOfId() != null) {
                continue;
            }
            // oferty z wysokim ryzykiem oszustwa (#19) zaniżałyby lub zawyżały obraz rynku
            if (ScamDetector.assess(o).level() == ScamAssessment.RiskLevel.HIGH) {
                continue;
            }
            NormalizedSalary n = normalizer.normalize(o);
            Integer mid = n.present() ? n.comparableMid() : null;
            if (mid == null || mid <= 0) {
                continue;
            }
            out.add(new Sample(o.getId(), o.getTitle(), o.getCompany(), o.getSeniority(),
                    o.getContractTypes() == null ? List.of() : List.copyOf(o.getContractTypes()), mid));
        }
        return out;
    }

    /** Mediana, kwartyle (interpolacja liniowa, jak PERCENTYL.PRZEDZ.ZAMK w arkuszu), min i max. */
    public static Distribution distribution(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return new Distribution(0, null, null, null, null, null, true);
        }
        List<Integer> sorted = values.stream().sorted().toList();
        return new Distribution(sorted.size(), percentile(sorted, 0.5), percentile(sorted, 0.25),
                percentile(sorted, 0.75), sorted.get(0), sorted.get(sorted.size() - 1), sorted.size() < SMALL_SAMPLE);
    }

    static int percentile(List<Integer> sorted, double p) {
        double pos = p * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        double v = sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (pos - lo);
        return (int) Math.round(v);
    }

    static String seniorityLabel(Seniority s) {
        return switch (s) {
            case INTERN -> "stażysta";
            case JUNIOR -> "junior";
            case MID -> "mid";
            case SENIOR -> "senior";
            case LEAD -> "lead";
        };
    }

    static String contractLabel(ContractType c) {
        return switch (c) {
            case UOP -> "Umowa o pracę";
            case B2B -> "B2B";
            case UZ -> "Umowa zlecenie";
            case UOD -> "Umowa o dzieło";
            case INTERNSHIP -> "Staż";
        };
    }

    static String ofert(int n) {
        if (n == 1) {
            return "oferta";
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "oferty" : "ofert";
    }
}

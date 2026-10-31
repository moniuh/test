package pl.radarpracy.salary.tools;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchLevel;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.matching.RequirementMatch;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryProperties;

import java.util.ArrayList;
import java.util.List;

import static pl.radarpracy.salary.tools.Fmt.zl;

/**
 * Asystent negocjacji (pomysł #66): kotwica, cel, kontroferta i próg wyjścia liczone deterministycznie
 * z widełek oferty, rynku (zebrane oferty) i minimum kandydata, plus gotowe sformułowania po polsku.
 *
 * <p>Wszystkie kwoty liczymy w równoważniku UoP brutto miesięcznie, a potem przeliczamy na formę oferty
 * (np. B2B netto + VAT, stawka godzinowa) tymi samymi współczynnikami co normalizacja.</p>
 */
@Service
public class NegotiationService {

    /** Kotwica = max(górna granica oferty, p75 rynku, minimum × 1,1). */
    static final double MINIMUM_ANCHOR_FACTOR = 1.10;
    /** Cel = max(środek widełek, mediana rynku, minimum × 1,05). */
    static final double MINIMUM_TARGET_FACTOR = 1.05;
    static final int ROUND_TO = 500;

    /**
     * Kwota w dwóch postaciach.
     *
     * @param comparable równoważnik UoP brutto miesięcznie
     * @param inOffer    kwota w formie oferty (np. B2B netto + VAT miesięcznie lub stawka godzinowa)
     * @param text       opis po polsku, np. „27 500 zł netto + VAT (B2B) miesięcznie”
     */
    public record Amount(int comparable, Integer inOffer, String text) {
    }

    /** Składnik kotwicy z informacją, czy zdecydował o wyniku. */
    public record AnchorComponent(String label, int value, boolean decisive) {
    }

    /** Plan negocjacji dla oferty. */
    public record NegotiationPlan(Long offerId, String offerTitle, String company, boolean salaryPresent,
                                  String basis, String basisLabel, Integer offerMin, Integer offerMax,
                                  String marketGroup, int marketCount, Integer marketMedian, Integer marketP75,
                                  boolean smallSample, Integer candidateMinimum, Amount anchor, Amount target,
                                  Amount counterOffer, Amount walkAway, List<AnchorComponent> anchorComponents,
                                  String anchorExplanation, List<String> arguments, List<String> phrases,
                                  List<String> levers, List<String> avoid, List<String> notes) {
    }

    private final OfferService offers;
    private final SalaryNormalizer normalizer;
    private final SalaryStatsService stats;
    private final ProfileService profiles;
    private final MatchService matching;
    private final SalaryProperties props;

    public NegotiationService(OfferService offers, SalaryNormalizer normalizer, SalaryStatsService stats,
                              ProfileService profiles, MatchService matching, SalaryProperties props) {
        this.offers = offers;
        this.normalizer = normalizer;
        this.stats = stats;
        this.profiles = profiles;
        this.matching = matching;
        this.props = props;
    }

    public NegotiationPlan plan(Long offerId) {
        JobOffer o = offers.get(offerId);
        NormalizedSalary n = normalizer.normalize(o);
        SalaryStatsService.Market market = stats.marketFor(o);
        Integer minimum = profiles.profile().getMinMonthlySalary();
        MatchResult match = null;
        try {
            match = matching.match(offerId, null);
        } catch (RuntimeException e) {
            // brak CV nie blokuje negocjacji — argumenty będą ogólne
        }
        return plan(o, n, market, minimum, match);
    }

    NegotiationPlan plan(JobOffer o, NormalizedSalary n, SalaryStatsService.Market market, Integer minimum,
                         MatchResult match) {
        boolean present = n.present() && n.comparableMid() != null;
        Integer offerMin = present ? n.comparableMin() : null;
        Integer offerMax = present ? n.comparableMax() : null;
        SalaryStatsService.Distribution d = market.stats();
        boolean marketUsable = d.count() >= SalaryStatsService.MIN_SAME_LEVEL_PEERS;
        List<String> notes = new ArrayList<>();

        // --- kotwica
        List<AnchorComponent> components = new ArrayList<>();
        Integer upper = offerMax != null ? offerMax : offerMin;
        if (upper != null) {
            components.add(new AnchorComponent("Górna granica widełek oferty", upper, false));
        }
        if (marketUsable && d.p75() != null) {
            components.add(new AnchorComponent("75. percentyl rynku (" + market.groupLabel() + ")", d.p75(), false));
        } else {
            notes.add("Za mało ofert do porównania rynkowego (" + d.count() + ") — kotwica bez danych rynkowych.");
        }
        if (minimum != null && minimum > 0) {
            components.add(new AnchorComponent("Twoje minimum z profilu × 1,1", (int) Math.round(minimum * MINIMUM_ANCHOR_FACTOR),
                    false));
        } else {
            notes.add("Uzupełnij minimalne wynagrodzenie w profilu — wtedy plan uwzględni Twój próg.");
        }
        if (components.isEmpty()) {
            notes.add("Brak widełek, danych rynkowych i minimum w profilu — nie da się wyliczyć kwot. "
                    + "Zacznij od pytania o budżet przewidziany na stanowisko.");
            return new NegotiationPlan(o.getId(), o.getTitle(), o.getCompany(), present, n.basis(),
                    OfferPay.basisLabel(n.basis()), offerMin, offerMax, market.groupLabel(), d.count(), d.median(),
                    d.p75(), d.smallSample(), minimum, null, null, null, null, components, null,
                    arguments(match), List.of(askForBudgetPhrase()), levers(o), avoid(null, null, null, d), notes);
        }
        int maxValue = components.stream().mapToInt(AnchorComponent::value).max().orElseThrow();
        List<AnchorComponent> marked = new ArrayList<>();
        boolean decided = false;
        for (AnchorComponent c : components) {
            boolean decisive = !decided && c.value() == maxValue;
            decided |= decisive;
            marked.add(new AnchorComponent(c.label(), c.value(), decisive));
        }
        int anchor = roundUp(maxValue);
        String decisiveLabel = marked.stream().filter(AnchorComponent::decisive).findFirst().orElseThrow().label();

        // --- cel, kontroferta, próg wyjścia
        int targetRaw = 0;
        if (present) {
            targetRaw = Math.max(targetRaw, n.comparableMid());
        }
        if (marketUsable && d.median() != null) {
            targetRaw = Math.max(targetRaw, d.median());
        }
        if (minimum != null && minimum > 0) {
            targetRaw = Math.max(targetRaw, (int) Math.round(minimum * MINIMUM_TARGET_FACTOR));
        }
        int target = Math.min(anchor, roundNearest(targetRaw > 0 ? targetRaw : anchor));
        int counter = Math.max(target, roundNearest((anchor + target) / 2.0));
        Integer walkAwayValue = minimum != null && minimum > 0 ? minimum : offerMin;

        Amount anchorA = amount(anchor, o, n);
        Amount targetA = amount(target, o, n);
        Amount counterA = amount(counter, o, n);
        Amount walkA = walkAwayValue == null ? null : amount(walkAwayValue, o, n);
        String explanation = "Kotwica to najwyższa z kwot: " + String.join(", ", marked.stream()
                .map(c -> c.label() + " " + zl(c.value())).toList()) + " — zdecydowało: " + decisiveLabel
                + "; zaokrąglone w górę do " + ROUND_TO + " zł. Kwoty w równoważniku UoP brutto miesięcznie.";
        if (d.smallSample()) {
            notes.add("Dane rynkowe z małej próby (" + d.count() + " " + SalaryStatsService.ofert(d.count())
                    + ") — traktuj je orientacyjnie.");
        }
        if (!present) {
            notes.add("Oferta nie podaje widełek — najpierw zapytaj o budżet, a kotwicę podaj dopiero potem.");
        }

        List<String> phrases = new ArrayList<>();
        List<String> args = arguments(match);
        String skills = match != null && !match.matchedSkills().isEmpty()
                ? String.join(", ", match.matchedSkills().stream().limit(3).toList()) : null;
        if (!present) {
            phrases.add(askForBudgetPhrase());
        }
        phrases.add("Dziękuję za ofertę — ta rola bardzo mi odpowiada. Biorąc pod uwagę zakres obowiązków"
                + (skills != null ? ", moje doświadczenie (" + skills + ")" : "")
                + (marketUsable ? " i stawki na podobnych stanowiskach" : "") + ", oczekuję " + anchorA.text() + ".");
        if (marketUsable && d.median() != null) {
            phrases.add("Mediana podobnych ofert, które analizuję (" + market.groupLabel() + "), to około "
                    + zl(d.median()) + " brutto (równoważnik UoP) — moja propozycja jest w górnej części rynku, "
                    + "bo od pierwszego dnia wnoszę doświadczenie, którego szukacie.");
        }
        phrases.add("Doceniam tę propozycję. Czy jest przestrzeń, żebyśmy spotkali się na poziomie " + counterA.text()
                + "? Przy takiej kwocie mogę szybko podjąć decyzję.");
        String lever = levers(o).stream().filter(l -> !l.startsWith("Przegląd")).findFirst().orElseThrow();
        int dash = lever.indexOf(" — ");
        lever = dash > 0 ? lever.substring(0, dash) : lever;
        phrases.add("Jeśli budżet na wynagrodzenie zasadnicze jest zamknięty, czy możemy uzupełnić ofertę o "
                + Character.toLowerCase(lever.charAt(0)) + lever.substring(1) + " albo przegląd wynagrodzenia "
                + "po 6 miesiącach z jasno ustalonymi kryteriami?");
        if ("B2B_NET".equals(n.basis())) {
            phrases.add("Upewnię się: rozmawiamy o kwocie netto na fakturze, do której doliczam VAT? Dla porównania "
                    + "z umową o pracę to około " + zl(anchor) + " brutto.");
        }
        phrases.add("Dziękuję — potrzebuję jednego–dwóch dni, żeby spokojnie przemyśleć ofertę. Kiedy mogę wrócić "
                + "z odpowiedzią?");

        return new NegotiationPlan(o.getId(), o.getTitle(), o.getCompany(), present, n.basis(),
                OfferPay.basisLabel(n.basis()), offerMin, offerMax, market.groupLabel(), d.count(), d.median(), d.p75(),
                d.smallSample(), minimum, anchorA, targetA, counterA, walkA, marked, explanation, args, phrases,
                levers(o), avoid(anchor, offerMax, walkAwayValue, d), notes);
    }

    /** Przeliczenie z równoważnika UoP brutto na formę oferty. */
    Amount amount(int comparable, JobOffer o, NormalizedSalary n) {
        String basis = n.basis();
        if ("B2B_NET".equals(basis)) {
            int b2b = roundNearest(comparable * props.b2bToUopFactor());
            String text = zl(b2b) + " netto + VAT (B2B) miesięcznie";
            if (o.getSalaryPeriod() == SalaryPeriod.HOUR) {
                int hourly = (int) Math.round((double) b2b / props.hoursPerMonth());
                text = hourly + " zł/h netto + VAT (≈ " + zl(b2b) + " miesięcznie)";
                return new Amount(comparable, hourly, text);
            }
            if (!"PLN".equalsIgnoreCase(Text.nullToEmpty(o.getSalaryCurrency())) && o.getSalaryCurrency() != null) {
                Double fx = props.fx().get(o.getSalaryCurrency().toUpperCase());
                if (fx != null && fx > 0) {
                    int foreign = (int) (Math.round(b2b / fx / 100.0) * 100);
                    return new Amount(comparable, foreign, foreign + " " + o.getSalaryCurrency().toUpperCase()
                            + " netto + VAT miesięcznie (≈ " + zl(b2b) + ")");
                }
            }
            return new Amount(comparable, b2b, text);
        }
        if ("NET".equals(basis)) {
            int net = roundNearest(comparable / props.netToGrossFactor());
            return new Amount(comparable, net, zl(net) + " netto miesięcznie (≈ " + zl(comparable) + " brutto)");
        }
        return new Amount(comparable, comparable, zl(comparable) + " brutto miesięcznie");
    }

    private static List<String> arguments(MatchResult match) {
        List<String> out = new ArrayList<>();
        if (match == null || match.cvId() == null) {
            out.add("Dodaj CV w profilu — plan podpowie argumenty z Twojego doświadczenia.");
            return out;
        }
        if (match.mustHaveTotal() > 0) {
            out.add("Spełniasz " + match.mustHaveMet() + " z " + match.mustHaveTotal() + " wymagań obowiązkowych.");
        }
        match.requirements().stream()
                .filter(r -> r.level() == MatchLevel.FULL && r.evidenceVerified() && r.evidence() != null)
                .sorted((a, b) -> Boolean.compare(b.mustHave(), a.mustHave()))
                .limit(3)
                .forEach(r -> out.add(argument(r)));
        return out;
    }

    private static String argument(RequirementMatch r) {
        return "„" + Text.truncate(r.requirement(), 70) + "” — potwierdzone w CV: „" + r.evidence() + "”";
    }

    private static String askForBudgetPhrase() {
        return "Zanim podam kwotę: jaki budżet przewidujecie dla tego stanowiska i od czego zależy jego górna granica?";
    }

    static List<String> levers(JobOffer o) {
        List<String> out = new ArrayList<>();
        String all = Text.fold(String.join("\n", o.getBenefits() == null ? List.of() : o.getBenefits()) + "\n"
                + Text.nullToEmpty(o.getDescription()));
        boolean b2b = o.getContractTypes() != null && o.getContractTypes().contains(ContractType.B2B)
                && !o.getContractTypes().contains(ContractType.UOP);
        if (b2b && !OfferPay.mentionsPaidLeave(o)) {
            out.add("Płatną przerwę (20–26 dni) — na B2B to około 10% wartości kontraktu");
        }
        if (!all.contains("szkolen") && !all.contains("training") && !all.contains("konferencj")) {
            out.add("Budżet szkoleniowy i konferencje");
        }
        if (!all.contains("medyczn") && !all.contains("medical")) {
            out.add("Prywatną opiekę medyczną (także dla rodziny)");
        }
        if (o.getWorkMode() == WorkMode.ONSITE || o.getWorkMode() == WorkMode.HYBRID) {
            out.add("Więcej dni pracy zdalnej");
        }
        out.add("Przegląd wynagrodzenia po 6 miesiącach z jasnymi kryteriami");
        out.add("Jednorazowy bonus na start (np. rekompensata premii z obecnej pracy)");
        out.add(b2b ? "Okres wypowiedzenia i warunki zakończenia współpracy" : "Dodatkowe dni wolne");
        out.add("Tytuł stanowiska i ścieżkę awansu zapisaną w ofercie");
        return out;
    }

    private static List<String> avoid(Integer anchor, Integer offerMax, Integer walkAway,
                                      SalaryStatsService.Distribution d) {
        List<String> out = new ArrayList<>();
        if (walkAway != null) {
            out.add("Nie podawaj pierwszej kwoty niższej niż Twoje minimum (" + zl(walkAway)
                    + " brutto) — po niej trudno negocjować w górę.");
        }
        out.add("Nie myl brutto, netto i B2B — zawsze dopowiadaj, o jaką kwotę i formę umowy chodzi.");
        out.add("Nie uzasadniaj oczekiwań kosztami życia — mów o wartości, którą wnosisz, i o danych rynkowych.");
        out.add("Nie akceptuj od razu i nie stawiaj ultimatum — poproś o czas na decyzję.");
        out.add("Nie powołuj się na oferty konkurencji, których nie masz.");
        if (anchor != null && offerMax != null && anchor > offerMax * 1.2) {
            int pct = (int) Math.round(100.0 * (anchor - offerMax) / offerMax);
            out.add("Kotwica przekracza górną granicę widełek o " + pct + "% — przygotuj mocne argumenty "
                    + "albo licz się z odmową.");
        }
        if (d.smallSample() && d.count() > 0) {
            out.add("Nie przedstawiaj danych rynkowych jako twardych statystyk — to tylko " + d.count() + " "
                    + SalaryStatsService.ofert(d.count()) + ".");
        }
        return out;
    }

    static int roundUp(double v) {
        return (int) (Math.ceil(v / ROUND_TO) * ROUND_TO);
    }

    static int roundNearest(double v) {
        return (int) (Math.round(v / ROUND_TO) * ROUND_TO);
    }
}

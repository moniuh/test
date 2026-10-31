package pl.radarpracy.salary.tools;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prosta, jawna tabela wycen typowych benefitów (pomysł #64; zalążek #67).
 *
 * <p>Wartości to <b>szacunek</b> typowej miesięcznej wartości dla pracownika (ceny pakietów indywidualnych,
 * typowe budżety), a nie dane z oferty — UI to mówi, a kandydat może dopisać własną kwotę.</p>
 */
public final class BenefitCatalog {

    /**
     * Wycena jednego benefitu.
     *
     * @param name    benefit tak, jak zapisano go w ofercie
     * @param matched pod jaką pozycję tabeli wycen go zaliczono (null — nie rozpoznano)
     * @param monthly wartość miesięcznie w zł (0 — nie wyceniamy)
     * @param note    jak wyceniono
     */
    public record BenefitValue(String name, String matched, double monthly, String note) {
    }

    private record Rule(String label, Pattern pattern, double monthly, String note) {
    }

    /** Domyślny roczny budżet szkoleniowy, gdy oferta nie podaje kwoty [szacunek]. */
    static final double DEFAULT_TRAINING_BUDGET_YEARLY = 1_500;

    private static final List<Rule> RULES = List.of(
            new Rule("Prywatna opieka medyczna", Pattern.compile("opiek\\p{L}* medyczn|pakiet medyczn|medical|luxmed|medicover(?! sport)|enel-med"),
                    150, "typowa cena indywidualnego pakietu medycznego"),
            new Rule("Karta sportowa", Pattern.compile("kart\\p{L}* sportow|multisport|medicover sport|fitprofit|sport card|gym"),
                    120, "wartość karty po typowej dopłacie pracownika"),
            new Rule("PPK", Pattern.compile("\\bppk\\b|pracownicze plany kapitalowe"),
                    -1, "wpłata pracodawcy 1,5% brutto (tylko UoP i zlecenie)"),
            new Rule("Budżet szkoleniowy", Pattern.compile("szkolen|training|konferencj|certyfikac|kursy|udemy"),
                    -2, "kwota z oferty ÷ 12 albo typowe 1 500 zł rocznie"),
            new Rule("Ubezpieczenie na życie", Pattern.compile("ubezpieczen\\p{L}* (?:na zycie|grupow)|life insurance"),
                    40, "typowa składka grupowa"),
            new Rule("Posiłki / karta lunchowa", Pattern.compile("lunch|posilk|obiad|meal|stolowk"),
                    200, "typowe dofinansowanie posiłków"),
            new Rule("Dofinansowanie dojazdu / parking", Pattern.compile("dojazd|parking|bilet"),
                    150, "typowy koszt biletu lub miejsca parkingowego"),
            new Rule("Budżet na sprzęt / home office", Pattern.compile("budzet na sprzet|home office|dodatek na prace zdaln|ryczalt na prace zdaln|equipment budget"),
                    80, "typowy budżet rozłożony na miesiące"),
            new Rule("Premia", Pattern.compile("premi|bonus"),
                    0, "zależy od wyników — nie wyceniamy; wpisz średnią premię w polu „inne benefity”"),
            new Rule("Akcje / opcje", Pattern.compile("\\bakcj|\\besop|opcje na akcje|stock|equity"),
                    0, "wartość niepewna — nie wyceniamy"),
            new Rule("Płatny urlop / przerwa", Pattern.compile("platn\\p{L}* (?:\\p{L}+ )?(?:przerw|urlop|wolne)|paid (?:time off|leave|vacation)|\\bpto\\b"),
                    0, "uwzględnione w korekcie urlopowej, nie jako benefit"),
            new Rule("Narzędzia pracy", Pattern.compile("macbook|laptop|komputer|sprzet"),
                    0, "narzędzie pracy — nie wyceniamy"),
            new Rule("Elastyczność / praca zdalna", Pattern.compile("elastyczn|zdaln|remote|workation|hybryd"),
                    0, "korzyść pozafinansowa — koszty dojazdu wpisz osobno"));

    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d{1,3}(?:[ \\u00a0.]\\d{3})+|\\d+)\\s*(k|tys\\.?)?\\s*(?:zl|pln)");

    private BenefitCatalog() {
    }

    /**
     * Wycena benefitów z oferty.
     *
     * @param benefits     benefity zapisane w ofercie
     * @param description  treść ogłoszenia (szukamy w niej kwoty budżetu szkoleniowego)
     * @param grossMonthly brutto miesięcznie (do PPK); null — PPK nie wyceniamy
     */
    static List<BenefitValue> value(List<String> benefits, String description, Double grossMonthly) {
        List<BenefitValue> out = new ArrayList<>();
        if (benefits == null) {
            return out;
        }
        for (String b : benefits) {
            if (Text.isBlank(b)) {
                continue;
            }
            String folded = Text.fold(b);
            Rule rule = RULES.stream().filter(r -> r.pattern().matcher(folded).find()).findFirst().orElse(null);
            if (rule == null) {
                out.add(new BenefitValue(b, null, 0, "nie wyceniamy automatycznie — wpisz wartość w polu „inne benefity”"));
            } else if (rule.monthly() == -1) {
                if (grossMonthly == null) {
                    out.add(new BenefitValue(b, rule.label(), 0, "PPK dotyczy tylko UoP i zlecenia"));
                } else {
                    double v = Fmt.round2(grossMonthly * TaxParameters2026.PPK_EMPLOYER_RATE);
                    out.add(new BenefitValue(b, rule.label(), v, rule.note()));
                }
            } else if (rule.monthly() == -2) {
                Double yearly = trainingBudget(b, description);
                double v = Fmt.round2((yearly != null ? yearly : DEFAULT_TRAINING_BUDGET_YEARLY) / 12);
                out.add(new BenefitValue(b, rule.label(), v, yearly != null
                        ? "kwota z oferty (" + Fmt.zl(yearly) + " rocznie) ÷ 12"
                        : "oferta nie podaje kwoty — typowe " + Fmt.zl(DEFAULT_TRAINING_BUDGET_YEARLY) + " rocznie ÷ 12"));
            } else {
                out.add(new BenefitValue(b, rule.label(), rule.monthly(), rule.note()));
            }
        }
        return out;
    }

    /** Kwota budżetu szkoleniowego rocznie: najpierw z benefitu, potem z linii opisu o szkoleniach. */
    static Double trainingBudget(String benefit, String description) {
        Double fromBenefit = amountIn(benefit);
        if (fromBenefit != null) {
            return fromBenefit;
        }
        for (String line : Text.lines(description)) {
            String f = Text.fold(line);
            if (f.contains("szkolen") || f.contains("training")) {
                Double v = amountIn(line);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Double amountIn(String text) {
        if (text == null) {
            return null;
        }
        String f = Text.fold(text);
        Matcher m = AMOUNT.matcher(f);
        if (!m.find()) {
            return null;
        }
        double v = Double.parseDouble(m.group(1).replaceAll("[ \\u00a0.]", ""));
        if (m.group(2) != null) {
            v *= 1000;
        }
        boolean monthly = f.contains("mies") || f.contains("month");
        return monthly ? v * 12 : v;
    }
}

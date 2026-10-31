package pl.radarpracy.salary;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rozpoznaje widełki płacowe w tekście ogłoszenia (część pomysłu #17).
 *
 * <p>Obsługuje m.in. „15 000 – 20 000 PLN brutto”, „18k-24k PLN netto + VAT (B2B)”,
 * „120–150 zł/h”, „do 25 000 zł”, „5 500 - 6 500 EUR”, „120 000 PLN rocznie”.</p>
 */
public final class SalaryParser {

    private static final String NUM = "(\\d{1,3}(?:[ \\u00a0.,]\\d{3})+|\\d+(?:[.,]\\d{1,2})?)\\s*(k|tys\\.?|tys)?";
    private static final String CUR = "(pln|zl|eur|euro|€|usd|\\$|gbp|£|chf)(?![a-z])";
    private static final Pattern AFTER = Pattern.compile(
            "(?:(od|from|do|up to|max\\.?|min\\.?)\\s*)?" + NUM + "\\s*(?:(?:-|–|—|do|to)\\s*" + NUM + ")?\\s*" + CUR);
    private static final Pattern BEFORE = Pattern.compile(
            CUR + "\\s*" + NUM + "\\s*(?:(?:-|–|—|do|to)\\s*" + NUM + ")?");

    private SalaryParser() {
    }

    public static Optional<SalaryRange> parse(String text) {
        if (Text.isBlank(text)) {
            return Optional.empty();
        }
        String f = Text.fold(text);
        Matcher m = AFTER.matcher(f);
        while (m.find()) {
            Optional<SalaryRange> r = build(text, f, m.start(), m.end(), m.group(1), m.group(2), m.group(3), m.group(4),
                    m.group(5), m.group(6));
            if (r.isPresent()) {
                return r;
            }
        }
        Matcher b = BEFORE.matcher(f);
        while (b.find()) {
            Optional<SalaryRange> r = build(text, f, b.start(), b.end(), null, b.group(2), b.group(3), b.group(4),
                    b.group(5), b.group(1));
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    private static Optional<SalaryRange> build(String original, String folded, int start, int end, String bound,
                                               String n1, String k1, String n2, String k2, String cur) {
        Integer a = amount(n1, k1);
        Integer b = n2 == null ? null : amount(n2, k2 != null ? k2 : k1);
        if (a == null || a < 10) {
            return Optional.empty();
        }
        // „18-24k” — mnożnik tylko przy drugiej liczbie
        if (b != null && k1 == null && k2 != null && a < 1000 && b >= 1000) {
            a = a * 1000;
        }
        Integer min = a;
        Integer max = b;
        if (b == null && bound != null) {
            String bd = bound.strip();
            if (bd.startsWith("do") || bd.startsWith("up") || bd.startsWith("max")) {
                min = null;
                max = a;
            }
        }
        if (min != null && max != null && max < min) {
            Integer t = min;
            min = max;
            max = t;
        }
        String context = folded.substring(start, Math.min(folded.length(), end + 60));
        String wide = folded.substring(Math.max(0, start - 80), Math.min(folded.length(), end + 80));
        SalaryPeriod period = period(context, max != null ? max : min);
        ContractType hint = contract(wide);
        Boolean gross = gross(context, wide, hint);
        String matched = original.substring(start, end).strip();
        return Optional.of(new SalaryRange(min, max, currency(cur), period, gross, hint, matched));
    }

    static Integer amount(String raw, String multiplier) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace(" ", " ").strip();
        BigDecimal value;
        if (s.matches("\\d{1,3}(?:[ .,]\\d{3})+")) {
            value = new BigDecimal(s.replaceAll("[ .,]", ""));
        } else {
            value = new BigDecimal(s.replace(',', '.'));
        }
        if (multiplier != null) {
            value = value.multiply(BigDecimal.valueOf(1000));
        }
        if (value.compareTo(BigDecimal.valueOf(10_000_000)) > 0) {
            return null;
        }
        return value.intValue();
    }

    private static String currency(String c) {
        return switch (c) {
            case "eur", "euro", "€" -> "EUR";
            case "usd", "$" -> "USD";
            case "gbp", "£" -> "GBP";
            case "chf" -> "CHF";
            default -> "PLN";
        };
    }

    private static SalaryPeriod period(String ctx, Integer amount) {
        if (ctx.matches("(?s).*(/\\s*h\\b|/\\s*godz|na godzin|za godzin|per hour|hourly|/hour|\\bh\\b netto|stawka godzinowa).*")) {
            return SalaryPeriod.HOUR;
        }
        if (ctx.matches("(?s).*(/\\s*(dzien|day|md)\\b|dziennie|za dzien|per day|daily|man-day|/\\s*md).*")) {
            return SalaryPeriod.DAY;
        }
        if (ctx.matches("(?s).*(rocznie|/\\s*rok|na rok|per year|annual|yearly|/\\s*year|p\\.a\\.).*")) {
            return SalaryPeriod.YEAR;
        }
        if (ctx.matches("(?s).*(mies|/\\s*m\\b|month|monthly).*")) {
            return SalaryPeriod.MONTH;
        }
        if (amount != null && amount < 500) {
            return SalaryPeriod.HOUR;
        }
        if (amount != null && amount >= 100_000) {
            return SalaryPeriod.YEAR;
        }
        return SalaryPeriod.MONTH;
    }

    private static ContractType contract(String wide) {
        if (wide.contains("b2b") || wide.contains("+ vat") || wide.contains("+vat") || wide.contains("faktur")
                || wide.contains("kontrakt")) {
            return ContractType.B2B;
        }
        if (wide.contains("uop") || wide.contains("umowa o prace") || wide.contains("umowie o prace")
                || wide.contains("employment contract") || wide.contains("permanent")) {
            return ContractType.UOP;
        }
        if (wide.contains("zlecen")) {
            return ContractType.UZ;
        }
        return null;
    }

    private static Boolean gross(String ctx, String wide, ContractType hint) {
        if (hint == ContractType.B2B) {
            return true;
        }
        if (ctx.contains("brutto") || ctx.contains("gross")) {
            return true;
        }
        if (ctx.contains("netto") || ctx.contains(" net") || ctx.contains("na reke") || ctx.contains("na konto")) {
            return false;
        }
        if (wide.contains("brutto") || wide.contains("gross")) {
            return true;
        }
        if (wide.contains("netto") || wide.contains("na reke")) {
            return false;
        }
        return null;
    }
}

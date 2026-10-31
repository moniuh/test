package pl.radarpracy.salary.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Zaokrąglanie i polski zapis kwot w opisach (np. „282 600 zł”, „9,76%”). */
final class Fmt {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    static {
        SYMBOLS.setGroupingSeparator(' ');
        SYMBOLS.setDecimalSeparator(',');
    }

    private Fmt() {
    }

    /** Zaokrąglenie do groszy (połówki w górę), jak w listach płac. */
    static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Zaokrąglenie do pełnych złotych (podstawa opodatkowania, podatek). */
    static double round0(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }

    /** Kwota w złotych bez groszy: „12 345 zł”. */
    static String zl(double v) {
        return new DecimalFormat("#,##0", SYMBOLS).format(v) + " zł";
    }

    /** Kwota w złotych z groszami, gdy są: „1 441,80 zł”. */
    static String zl2(double v) {
        return new DecimalFormat(v == Math.rint(v) ? "#,##0" : "#,##0.00", SYMBOLS).format(v) + " zł";
    }

    /** Stawka jako procent: 0.0976 → „9,76%”. */
    static String pct(double rate) {
        return BigDecimal.valueOf(rate).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                .replace('.', ',') + "%";
    }

    /** Wartość procentowa podana wprost (np. stawka ryczałtu 8.5 → „8,5%”). */
    static String pctValue(double percent) {
        return BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}

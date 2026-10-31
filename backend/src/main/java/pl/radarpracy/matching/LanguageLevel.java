package pl.radarpracy.matching;

import pl.radarpracy.common.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Poziomy CEFR (A1–C2) rozpoznawane z kodu lub z opisu słownego. */
final class LanguageLevel {

    private static final Pattern CEFR = Pattern.compile("(?<![a-z0-9])([abc])\\s?([12])(?![0-9])");
    private static final String[][] WORDS = {
            {"native", "6"}, {"ojczyst", "6"}, {"proficient", "6"},
            {"bardzo dobr", "5"}, {"very good", "5"}, {"biegl", "5"}, {"fluent", "5"}, {"zaawansowan", "5"},
            {"advanced", "5"},
            {"upper-intermediate", "4"}, {"upper intermediate", "4"}, {"dobr", "4"}, {"good", "4"},
            {"komunikatywn", "3"}, {"sredniozaawansowan", "3"}, {"intermediate", "3"}, {"swobodn", "4"},
            {"podstawow", "2"}, {"basic", "2"}, {"elementary", "2"}};

    private LanguageLevel() {
    }

    /** Poziom 1–6 (A1=1 … C2=6) albo null. */
    static Integer parse(String text) {
        String f = Text.fold(text);
        Matcher m = CEFR.matcher(f);
        Integer best = null;
        while (m.find()) {
            int level = (m.group(1).charAt(0) - 'a') * 2 + Integer.parseInt(m.group(2));
            best = best == null ? level : Math.max(best, level);
        }
        if (best != null) {
            return best;
        }
        for (String[] w : WORDS) {
            if (f.contains(w[0])) {
                return Integer.parseInt(w[1]);
            }
        }
        return null;
    }

    static String label(int level) {
        char letter = (char) ('A' + (level - 1) / 2);
        return "" + letter + ((level - 1) % 2 + 1);
    }
}

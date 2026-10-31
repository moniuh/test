package pl.radarpracy.offer;

import pl.radarpracy.common.Text;

import java.time.Year;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rozpoznaje liczbę lat doświadczenia w wymaganiach („min. 3 lata”, „5+ years”) i w CV („2019 – obecnie”). */
public final class YearsParser {

    /** Jednostka: lat/lata/latami/latach, rok/roku/rokiem, years. */
    private static final String UNIT = "(?:lat\\w*|rok\\w*|years?|yrs?)\\b";
    /** Liczba lat, także z częścią dziesiętną („2,5”) — liczymy pełne lata; nie zaczyna się w środku liczby. */
    private static final String N = "(?<![\\d.,])(\\d{1,2})(?:[.,]\\d)?";
    private static final Pattern MIN_PREFIX = Pattern.compile(
            "(?:min\\.?|minimum|co najmniej|przynajmniej|at least|od|powyzej|over)\\s*" + N + "\\s*\\+?\\s*" + UNIT);
    private static final Pattern PLUS = Pattern.compile(N + "\\s*\\+\\s*" + UNIT);
    private static final Pattern RANGE = Pattern.compile(N + "\\s*(?:-|–|—|do|to)\\s*\\d{1,2}\\s*" + UNIT);
    private static final Pattern PLAIN = Pattern.compile(
            N + "\\s*(?:lat\\w*|rok\\w*|letni\\w*|roczn\\w*|years?|yrs?)\\b");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2})\\s*(?:-|–|—|do|to)\\s*((?:19|20)\\d{2}|obecnie|teraz|nadal|now|present|current|dzis)");
    private static final Map<String, Integer> WORDS = Map.ofEntries(
            Map.entry("roczn", 1), Map.entry("jednoroczn", 1), Map.entry("dwuletni", 2), Map.entry("trzyletni", 3),
            Map.entry("czteroletni", 4), Map.entry("pieciolet", 5), Map.entry("dwoch lat", 2), Map.entry("trzech lat", 3),
            Map.entry("czterech lat", 4), Map.entry("pieciu lat", 5), Map.entry("dwa lata", 2), Map.entry("trzy lata", 3),
            Map.entry("cztery lata", 4), Map.entry("two years", 2), Map.entry("three years", 3),
            Map.entry("four years", 4), Map.entry("five years", 5));

    private YearsParser() {
    }

    /** Minimalna liczba lat wymagana w treści wymagania albo null. */
    public static Integer requiredYears(String requirement) {
        String f = Text.fold(requirement);
        for (Pattern p : new Pattern[]{MIN_PREFIX, PLUS, RANGE, PLAIN}) {
            Matcher m = p.matcher(f);
            if (m.find()) {
                int years = Integer.parseInt(m.group(1));
                if (years > 0 && years <= 25) {
                    return years;
                }
            }
        }
        for (var e : WORDS.entrySet()) {
            if (f.contains(e.getKey()) && (f.contains("doswiadcz") || f.contains("experience"))) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Największa liczba lat wynikająca z fragmentu CV: jawne „5 lat” albo zakres dat „2019 – obecnie”.
     * Zwraca null, gdy fragment nic nie mówi o czasie.
     */
    public static Integer yearsInText(String text) {
        String f = Text.fold(text);
        Integer best = null;
        Matcher m = PLAIN.matcher(f);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y > 0 && y <= 40) {
                best = best == null ? y : Math.max(best, y);
            }
        }
        Matcher d = DATE_RANGE.matcher(f);
        int now = Year.now().getValue();
        while (d.find()) {
            int from = Integer.parseInt(d.group(1));
            String toRaw = d.group(2);
            int to = Character.isDigit(toRaw.charAt(0)) ? Integer.parseInt(toRaw) : now;
            int y = to - from;
            if (y >= 0 && y <= 40) {
                int years = Math.max(1, y);
                best = best == null ? years : Math.max(best, years);
            }
        }
        return best;
    }
}

package pl.radarpracy.ai;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CandidateProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maskowanie danych osobowych przed wysłaniem tekstu do modelu (pomysł #82).
 *
 * <p>Zastępuje imię i nazwisko z profilu, e-maile, telefony, PESEL, numery kont, adresy, kody
 * pocztowe, daty urodzenia i linki do profili znacznikami, np. {@code [E-MAIL-1]}.
 * {@link Masked#unmask(String)} przywraca oryginały w odpowiedzi modelu.</p>
 */
@Component
public class PiiMasker {

    /** Wynik maskowania: tekst ze znacznikami i mapa znacznik → oryginał. */
    public record Masked(String text, Map<String, String> replacements) {

        public String unmask(String s) {
            if (s == null) {
                return null;
            }
            String out = s;
            for (var e : replacements.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        }

        /** Liczba zamaskowanych wystąpień według kategorii (do pokazania użytkownikowi). */
        public Map<String, Integer> summary() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (String key : replacements.keySet()) {
                String cat = key.replaceAll("^\\[|(-\\d+)?]$", "");
                out.merge(cat, 1, Integer::sum);
            }
            return out;
        }
    }

    private record Rule(String label, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("E-MAIL", Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+")),
            new Rule("LINK", Pattern.compile("(?i)(?:https?://)?(?:www\\.)?(?:linkedin\\.com/in|github\\.com|gitlab\\.com|facebook\\.com)/[\\w%./-]+")),
            new Rule("NR KONTA", Pattern.compile("(?i)\\b(?:PL)?\\s?\\d{2}(?:[ ]?\\d{4}){6}\\b")),
            new Rule("PESEL", Pattern.compile("(?<!\\d)\\d{11}(?!\\d)")),
            new Rule("TELEFON", Pattern.compile("(?<![\\w\\d])(?:\\+|00)?(?:48[ -]?)?(?:\\(?\\d{2,3}\\)?[ -]?)?\\d{3}[ -]?\\d{2,3}[ -]?\\d{2,3}(?![\\w\\d])")),
            new Rule("DATA URODZENIA", Pattern.compile("(?i)(?:data urodzenia|ur\\.|urodzon[ya]|date of birth|born)[:\\s]*\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}")),
            new Rule("ADRES", Pattern.compile("(?i)\\b(?:ul\\.|ulica|al\\.|aleja|os\\.|pl\\.)\\s+[\\p{L}.\\- ]{2,40}?\\s\\d+[a-z]?(?:\\s?/\\s?\\d+[a-z]?)?")),
            new Rule("KOD POCZTOWY", Pattern.compile("\\b\\d{2}-\\d{3}\\b")));

    public Masked mask(String text, CandidateProfile profile) {
        Map<String, String> replacements = new LinkedHashMap<>();
        if (text == null) {
            return new Masked("", replacements);
        }
        String out = text;
        // imię i nazwisko z profilu — najpierw pełne, potem pojedyncze człony (≥ 3 litery)
        if (profile != null && !Text.isBlank(profile.getFullName())) {
            String full = profile.getFullName().strip();
            if (out.contains(full)) {
                out = out.replace(full, "[KANDYDAT]");
                replacements.put("[KANDYDAT]", full);
            }
            String[] parts = full.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.length() >= 3) {
                    Pattern p = Pattern.compile("(?<!\\p{L})" + Pattern.quote(part) + "(?!\\p{L})");
                    Matcher m = p.matcher(out);
                    if (m.find()) {
                        String tag = i == 0 ? "[IMIĘ]" : "[NAZWISKO]";
                        out = m.replaceAll(Matcher.quoteReplacement(tag));
                        replacements.putIfAbsent(tag, part);
                    }
                }
            }
        }
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(out);
            StringBuilder sb = new StringBuilder();
            int counter = (int) replacements.keySet().stream().filter(k -> k.startsWith("[" + rule.label() + "-")).count();
            while (m.find()) {
                String value = m.group();
                if (rule.label().equals("PESEL") && !validPesel(value)) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value));
                    continue;
                }
                if (rule.label().equals("TELEFON") && !plausiblePhone(value.replaceAll("\\D", ""))) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value));
                    continue;
                }
                String existing = replacements.entrySet().stream().filter(e -> e.getValue().equals(value))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                String tag = existing != null ? existing : "[" + rule.label() + "-" + (++counter) + "]";
                replacements.putIfAbsent(tag, value);
                m.appendReplacement(sb, Matcher.quoteReplacement(tag));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return new Masked(out, replacements);
    }

    /** Polski numer: 9 cyfr albo 11/13 cyfr z prefiksem 48/0048. */
    static boolean plausiblePhone(String digits) {
        return digits.length() == 9 || (digits.length() == 11 && digits.startsWith("48"))
                || (digits.length() == 13 && digits.startsWith("0048"));
    }

    static boolean validPesel(String s) {
        if (!s.matches("\\d{11}")) {
            return false;
        }
        int[] w = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (s.charAt(i) - '0') * w[i];
        }
        int control = (10 - sum % 10) % 10;
        return control == s.charAt(10) - '0';
    }

    /** Lista kategorii danych, które maskujemy — do wyświetlenia w interfejsie. */
    public static List<String> categories() {
        List<String> out = new ArrayList<>(List.of("IMIĘ I NAZWISKO"));
        RULES.forEach(r -> out.add(r.label()));
        return out;
    }
}

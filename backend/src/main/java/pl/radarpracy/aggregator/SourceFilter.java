package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.NormalizedSalary;
import pl.radarpracy.salary.SalaryNormalizer;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ocena pozycji względem filtrów źródła (pomysł #60). Słowa kluczowe dopasowujemy jako całe słowa lub
 * frazy po {@link Text#fold} (bez wielkości liter i polskich znaków); „java*” oznacza prefiks.
 * Brak danych (lokalizacji, widełek) nie odrzuca oferty — filtr odrzuca tylko to, co na pewno nie pasuje.
 */
public final class SourceFilter {

    /** Decyzja: przyjęta albo odrzucona z powodem po polsku. */
    public record Decision(boolean accepted, String reason) {

        static Decision accept() {
            return new Decision(true, null);
        }

        static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }

    private static final Set<String> REMOTE_WORDS = Set.of("zdalnie", "zdalna", "praca zdalna", "remote", "zdalny");

    private SourceFilter() {
    }

    public static Decision evaluate(SourceFilters f, JobOffer offer, SalaryNormalizer normalizer) {
        if (f == null || f.isEmpty()) {
            return Decision.accept();
        }
        String title = norm(offer.getTitle());
        String company = norm(offer.getCompany());
        String body = norm(offer.getDescription());

        if (!f.includeKeywords().isEmpty()) {
            boolean any = false;
            for (String kw : f.includeKeywords()) {
                if (matches(title, kw) || matches(company, kw) || matches(body, kw)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return Decision.reject("brak wymaganych słów kluczowych (" + String.join(", ", f.includeKeywords()) + ")");
            }
        }
        for (String kw : f.excludeKeywords()) {
            String where = matches(title, kw) ? "w tytule" : matches(company, kw) ? "w nazwie firmy"
                    : matches(body, kw) ? "w treści" : null;
            if (where != null) {
                return Decision.reject("wykluczone słowo „" + kw + "” " + where);
            }
        }
        if (!f.locations().isEmpty()) {
            Decision d = location(f.locations(), offer);
            if (!d.accepted()) {
                return d;
            }
        }
        if (f.minSalary() != null && normalizer != null) {
            NormalizedSalary s = normalizer.normalize(offer);
            if (s.present() && s.comparableMax() != null && s.comparableMax() < f.minSalary()) {
                NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pl-PL"));
                return Decision.reject("widełki do ok. " + nf.format(s.comparableMax())
                        + " zł (równoważnik UoP brutto) poniżej minimum " + nf.format(f.minSalary()) + " zł");
            }
        }
        return Decision.accept();
    }

    private static Decision location(List<String> wanted, JobOffer offer) {
        boolean remoteWanted = wanted.stream().anyMatch(w -> REMOTE_WORDS.contains(Text.fold(w).strip()));
        if (remoteWanted && offer.getWorkMode() == WorkMode.REMOTE) {
            return Decision.accept();
        }
        String loc = norm(offer.getLocation());
        if (loc.isBlank()) {
            return Decision.accept();
        }
        for (String w : wanted) {
            String fw = norm(w);
            if (REMOTE_WORDS.contains(fw)) {
                if (loc.contains("zdaln") || loc.contains("remote")) {
                    return Decision.accept();
                }
            } else if (!fw.isBlank() && loc.contains(fw)) {
                return Decision.accept();
            }
        }
        return Decision.reject("lokalizacja „" + offer.getLocation() + "” spoza listy (" + String.join(", ", wanted) + ")");
    }

    /** Dopasowanie słowa/frazy (całe słowa) albo prefiksu „słowo*” w tekście złożonym. */
    static boolean matches(String foldedText, String keyword) {
        String kw = norm(keyword);
        boolean prefix = kw.endsWith("*");
        if (prefix) {
            kw = kw.substring(0, kw.length() - 1).strip();
        }
        if (kw.isEmpty() || foldedText.isEmpty()) {
            return false;
        }
        int from = 0;
        while (true) {
            int idx = foldedText.indexOf(kw, from);
            if (idx < 0) {
                return false;
            }
            int end = idx + kw.length();
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(foldedText.charAt(idx - 1));
            boolean endOk = prefix || end >= foldedText.length() || !isWordChar(foldedText.charAt(end), kw);
            if (startOk && endOk) {
                return true;
            }
            from = idx + 1;
        }
    }

    private static boolean isWordChar(char next, String kw) {
        if (Character.isLetterOrDigit(next)) {
            return true;
        }
        // „c” nie pasuje do „c++” / „c#”
        char last = kw.charAt(kw.length() - 1);
        return Character.isLetterOrDigit(last) && (next == '+' || next == '#');
    }

    static String norm(String s) {
        return Text.fold(s).replaceAll("\\s+", " ").strip();
    }
}

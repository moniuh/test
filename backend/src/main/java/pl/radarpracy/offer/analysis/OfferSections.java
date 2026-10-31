package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.List;

/** Rozpoznawanie nagłówków sekcji ogłoszenia i wyodrębnianie listy benefitów. */
final class OfferSections {

    static final List<String> BENEFIT_HEADERS = List.of(
            "co oferujemy", "oferujemy", "w zamian oferujemy", "benefity", "benefits", "we offer", "what we offer",
            "zapewniamy", "co zyskasz", "co otrzymasz", "perks", "nasza oferta", "what you get", "what you will get",
            "dajemy", "gwarantujemy", "co mozemy ci zaoferowac");

    private static final List<String> OTHER_HEADERS = List.of(
            "wymagania", "wymagamy", "oczekujemy", "czego oczekujemy", "nasze oczekiwania", "requirements", "must have",
            "mile widzian", "nice to have", "obowiazki", "zakres obowiazkow", "twoje zadania", "zadania", "responsibilities",
            "your tasks", "o nas", "about us", "o firmie", "o projekcie", "about the project", "about the role",
            "proces rekrutacji", "recruitment process", "klauzula", "jak aplikowac", "how to apply", "jak zaczac",
            "kogo szukamy", "your profile", "twoj profil", "who you are", "tech stack", "lokalizacja", "wynagrodzenie",
            "kontakt", "aplikuj", "czym bedziesz", "your role", "twoja rola", "dlaczego warto", "why join");

    private static final List<String> STOP_LINES = List.of(
            "prosimy o", "wyrazam zgode", "aplikuj", "apply", "klauzula", "administratorem", "informujemy",
            "zastrzegamy", "skontaktujemy sie", "osoby zainteresowane", "oferta wazn", "ogloszenie wazn", "wazne do",
            "termin");

    private OfferSections() {
    }

    /** Czy linia jest nagłówkiem sekcji benefitów; zwraca treść po nagłówku (może być pusta) albo null. */
    static String benefitHeaderRest(String line) {
        String t = Evidence.stripDecorations(line).replaceAll("^[*_#\\s]+|[*_]+$", "");
        String f = Text.fold(t);
        for (String h : BENEFIT_HEADERS) {
            if (f.startsWith(h)) {
                String rest = t.substring(h.length()).replaceFirst("^[\\s*_]*[:\\-–—]?\\s*", "");
                // „Oferujemy elastyczne godziny pracy” — nagłówek z treścią w tej samej linii
                return rest;
            }
        }
        return null;
    }

    /** Czy linia wygląda na nagłówek innej sekcji (koniec listy benefitów). */
    static boolean isOtherHeader(String line) {
        String t = Evidence.stripDecorations(line);
        if (t.length() > 70) {
            return false;
        }
        String f = Text.fold(t).replaceAll("[:#*_]+", " ").strip();
        if (f.isEmpty()) {
            return false;
        }
        for (String h : OTHER_HEADERS) {
            if (f.startsWith(h)) {
                return true;
            }
        }
        return t.endsWith(":") && t.length() < 50 && !RequirementBullet.isBullet(line);
    }

    /**
     * Benefity z sekcji „Oferujemy / Benefity / We offer…”: punkty listy albo wyliczenie po
     * dwukropku („Benefity: karta sportowa, PPK”). Każdy element to dosłowny fragment treści.
     */
    static List<String> benefits(String text) {
        List<String> out = new ArrayList<>();
        if (Text.isBlank(text)) {
            return out;
        }
        // tryb sekcji: NONE = poza sekcją, START = po nagłówku, INLINE = wyliczenie w linii nagłówka,
        // BULLETS = lista z punktorami, PLAIN = krótkie linie bez punktorów
        enum Mode { NONE, START, INLINE, BULLETS, PLAIN }
        Mode mode = Mode.NONE;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (mode == Mode.PLAIN || mode == Mode.INLINE) {
                    mode = Mode.NONE; // pusta linia kończy listę bez punktorów
                }
                continue;
            }
            String rest = benefitHeaderRest(line);
            if (rest != null && line.length() < 300) {
                mode = Mode.START;
                if (!rest.isBlank()) {
                    addInline(out, rest);
                    mode = Mode.INLINE;
                }
                continue;
            }
            if (mode == Mode.NONE) {
                continue;
            }
            String folded = Text.fold(line);
            if (isOtherHeader(line) || STOP_LINES.stream().anyMatch(folded::startsWith)) {
                mode = Mode.NONE;
                continue;
            }
            boolean bullet = RequirementBullet.isBullet(line);
            if (mode == Mode.START || mode == Mode.INLINE) {
                if (!bullet && (mode == Mode.INLINE || line.length() > 120)) {
                    mode = Mode.NONE;
                    continue;
                }
                mode = bullet ? Mode.BULLETS : Mode.PLAIN;
            }
            if (mode == Mode.BULLETS && !bullet || mode == Mode.PLAIN && line.length() > 120) {
                mode = Mode.NONE; // akapit po liście — koniec sekcji
                continue;
            }
            add(out, Evidence.stripDecorations(line));
            if (out.size() >= 25) {
                break;
            }
        }
        return out;
    }

    private static void addInline(List<String> out, String rest) {
        String[] parts = rest.split("\\s*[,;•]\\s+|\\s*;\\s*");
        if (parts.length == 1) {
            add(out, rest);
            return;
        }
        for (String p : parts) {
            add(out, p);
        }
    }

    private static void add(List<String> out, String item) {
        String t = item.strip().replaceAll("[.;,]+$", "").strip();
        if (t.length() >= 2 && t.length() <= 160 && !out.contains(t)) {
            out.add(t);
        }
    }

    /** Rozpoznawanie punktorów list (jak w {@code RequirementExtractor}). */
    static final class RequirementBullet {
        private RequirementBullet() {
        }

        static boolean isBullet(String line) {
            String t = line.strip();
            return !t.isEmpty() && ("-•*·▪–—✓✔►>".indexOf(t.charAt(0)) >= 0 || t.matches("^\\d{1,2}[.)]\\s+.*"));
        }
    }
}

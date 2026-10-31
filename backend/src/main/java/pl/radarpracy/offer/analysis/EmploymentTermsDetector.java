package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.offer.YearsParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rozpoznaje tryb pracy, formę zatrudnienia i poziom stanowiska z treści ogłoszenia (pomysł #21).
 *
 * <p>Reguły są jawne i działają po polsku i po angielsku. Każde rozpoznanie zwraca dosłowny cytat
 * z ogłoszenia oraz pewność 0–1 (niska pewność = pole do sprawdzenia w interfejsie).</p>
 */
public final class EmploymentTermsDetector {

    /** Rozpoznana wartość z cytatem-dowodem i pewnością 0–1. */
    public record Detection<T>(T value, String quote, double confidence) {
    }

    // --- tryb pracy ---
    private static final Pattern STRONG_REMOTE = Evidence.p(
            "100\\s*%\\s*(?:zdaln\\w*|remote)|w pelni zdaln\\w*|calkowicie zdaln\\w*|wylacznie zdaln\\w*|tylko zdaln\\w*"
                    + "|\\bfull(?:y)?[- ]remote\\b|\\bremote[- ]first\\b|z dowolnego miejsca|work from anywhere");
    private static final Pattern HYBRID = Evidence.p(
            "hybryd\\w*|\\bhybrid\\w*|czesciowo zdaln\\w*|partially remote|czesciowo z biura"
                    + "|\\b[1-3]\\s*(?:x\\s*)?(?:dni|dzien|razy|days?|times)\\s*(?:w tygodniu\\s*|a week\\s*|per week\\s*|tygodniowo\\s*)?"
                    + "(?:w biurze|z biura|in (?:the )?office|w siedzibie|on[- ]?site)");
    private static final Pattern REMOTE = Evidence.p(
            "\\bzdaln\\w*|\\bremote\\b|praca z domu|prace z domu|work from home|\\bwfh\\b|\\bhome office\\b|\\btelepra\\w*");
    private static final Pattern ONSITE = Evidence.p(
            "stacjonarn\\w*|\\bz biura\\b|\\bw biurze\\b|\\bon[- ]?site\\b|\\bin[- ]office\\b|office[- ]based"
                    + "|praca na miejscu|w siedzibie (?:firmy|klienta)|work from (?:the )?office");
    private static final Pattern REMOTE_NEGATION = Evidence.p(
            "(?:brak|bez|nie ma|nie oferujemy|nie przewidujemy|\\bno\\b|\\bnot\\b|non)\\W+(?:\\w+\\W+){0,2}$");
    private static final Pattern REMOTE_PARTIAL = Evidence.p(
            "mozliwosc\\w*\\s+(?:\\w+\\s+){0,2}(?:pracy\\s+)?zdaln|okazjonaln\\w*\\s+(?:\\w+\\s+){0,1}zdaln|czasem zdaln");

    // --- forma zatrudnienia ---
    private static final Map<ContractType, Pattern> CONTRACTS = new EnumMap<>(ContractType.class);

    static {
        CONTRACTS.put(ContractType.UOP, Evidence.p(
                "umow\\w* o prac\\w*|\\buop\\b|\\bu\\.o\\.p\\.?|employment contract|contract of employment"
                        + "|permanent (?:contract|employment|position)|\\betat\\w*\\b|na etacie"));
        CONTRACTS.put(ContractType.B2B, Evidence.p(
                "\\bb2b\\b|\\+\\s*vat\\b|na faktur\\w*|\\bfaktur\\w*|wlasn\\w* dzialalnosc\\w*|\\bjdg\\b|self[- ]employ\\w*"
                        + "|\\bcontractor\\b|\\bkontrakt\\w*\\b|contract basis"));
        CONTRACTS.put(ContractType.UZ, Evidence.p(
                "umow\\w*[- ]zleceni\\w*|\\bna zleceniu\\b|mandate contract|contract of mandate"));
        CONTRACTS.put(ContractType.UOD, Evidence.p(
                "umow\\w* o dzielo|umow\\w*[- ]dzielo|\\bo dzielo\\b|contract for (?:a )?specific (?:work|task)"
                        + "|specific[- ]task contract"));
        CONTRACTS.put(ContractType.INTERNSHIP, Evidence.p(
                "\\bstaz(?:u|ysta|ystka|ystki|ysty|ystow|owy|owa|owe|owego|em)?\\b(?!\\s*pracy)"
                        + "|\\bpraktyk(?:i|a|ach)?\\s+(?:studenck|zawodow|absolwenck|letni|platn|wakacyjn)\\w*"
                        + "|\\bpraktykant\\w*|\\bprogram\\w* praktyk\\w*|\\binternship\\w*|\\bintern\\b|\\btrainee\\w*"
                        + "|\\bgraduate program\\w*"));
    }

    private static final Pattern YEARS_BEFORE = Evidence.p("(?:\\d|\\blat|\\brok|roczn|letn|minimum|\\bmin\\b)");

    // --- poziom ---
    private static final Map<Seniority, Pattern> TITLE_LEVELS = new EnumMap<>(Seniority.class);

    static {
        TITLE_LEVELS.put(Seniority.INTERN, Evidence.p(
                "\\b(?:intern|internship|stazyst\\w*|praktykant\\w*|trainee|staz\\w*)\\b"));
        TITLE_LEVELS.put(Seniority.JUNIOR, Evidence.p(
                "\\b(?:junior|jr|mlodsz\\w*|poczatkujac\\w*|entry[- ]level|absolwent\\w*|graduate)\\b"));
        TITLE_LEVELS.put(Seniority.MID, Evidence.p(
                "\\b(?:mid|mid[- ]level|regular|medior|intermediate)\\b"));
        TITLE_LEVELS.put(Seniority.SENIOR, Evidence.p(
                "\\b(?:senior|sr|starsz\\w*|ekspert\\w*|expert|staff|architekt\\w*|architect)\\b"));
        TITLE_LEVELS.put(Seniority.LEAD, Evidence.p(
                "\\b(?:tech(?:nical)?[- ]lead|team[- ]lead\\w*|lead|leader|lider\\w*|kierownik\\w*|head of"
                        + "|engineering manager|principal|dyrektor\\w*|director|cto)\\b"));
    }

    private static final Pattern TEXT_LEVEL = Evidence.p(
            "(?:poziom\\w*|level|stanowisk\\w*|na stanowisko|rol\\w*)\\s+(junior|mid|regular|senior)\\b"
                    + "|\\b(junior|mid|senior)[- ]level\\b");
    private static final Pattern NO_EXPERIENCE = Evidence.p(
            "bez doswiadczenia|nie wymagamy doswiadczenia|doswiadczenie nie jest wymagane|no (?:prior )?experience"
                    + "|zero experience|dla (?:osob )?poczatkujacych|pierwsza praca");

    private EmploymentTermsDetector() {
    }

    // =====================================================================================
    // Tryb pracy
    // =====================================================================================

    /** Tryb pracy: zdalna / hybrydowa / stacjonarna. */
    public static Optional<Detection<WorkMode>> workMode(String text) {
        if (Text.isBlank(text)) {
            return Optional.empty();
        }
        String folded = Text.fold(text);
        Optional<Evidence.Hit> strong = firstNotNegated(text, folded, STRONG_REMOTE);
        if (strong.isPresent()) {
            return Optional.of(new Detection<>(WorkMode.REMOTE, strong.get().quote(), 0.9));
        }
        Optional<Evidence.Hit> hybrid = Evidence.find(text, HYBRID);
        if (hybrid.isPresent()) {
            return Optional.of(new Detection<>(WorkMode.HYBRID, hybrid.get().quote(), 0.9));
        }
        Optional<Evidence.Hit> remote = firstNotNegated(text, folded, REMOTE);
        Optional<Evidence.Hit> negatedRemote = firstNegated(text, folded, REMOTE);
        Optional<Evidence.Hit> onsite = Evidence.find(text, ONSITE);
        if (remote.isPresent() && onsite.isPresent()) {
            Optional<Evidence.Hit> partial = Evidence.find(text, REMOTE_PARTIAL);
            if (partial.isPresent()) {
                return Optional.of(new Detection<>(WorkMode.HYBRID, partial.get().quote(), 0.7));
            }
            Evidence.Hit first = remote.get().start() <= onsite.get().start() ? remote.get() : onsite.get();
            WorkMode mode = first == remote.get() ? WorkMode.REMOTE : WorkMode.ONSITE;
            return Optional.of(new Detection<>(mode, first.quote(), 0.55));
        }
        if (remote.isPresent()) {
            Optional<Evidence.Hit> partial = Evidence.find(text, REMOTE_PARTIAL);
            if (partial.isPresent()) {
                return Optional.of(new Detection<>(WorkMode.HYBRID, partial.get().quote(), 0.6));
            }
            return Optional.of(new Detection<>(WorkMode.REMOTE, remote.get().quote(), 0.8));
        }
        if (onsite.isPresent()) {
            return Optional.of(new Detection<>(WorkMode.ONSITE, onsite.get().quote(), 0.8));
        }
        return negatedRemote.map(h -> new Detection<>(WorkMode.ONSITE, h.quote(), 0.7));
    }

    private static Optional<Evidence.Hit> firstNotNegated(String text, String folded, Pattern p) {
        for (Evidence.Hit h : Evidence.findAll(text, p, 20)) {
            if (!negated(folded, h.start())) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    private static Optional<Evidence.Hit> firstNegated(String text, String folded, Pattern p) {
        for (Evidence.Hit h : Evidence.findAll(text, p, 20)) {
            if (negated(folded, h.start())) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    private static boolean negated(String folded, int start) {
        String before = folded.substring(Math.max(0, start - 40), start);
        int nl = before.lastIndexOf('\n');
        if (nl >= 0) {
            before = before.substring(nl + 1);
        }
        return REMOTE_NEGATION.matcher(before).find();
    }

    // =====================================================================================
    // Forma zatrudnienia
    // =====================================================================================

    /** Formy zatrudnienia wymienione w ogłoszeniu, w kolejności pierwszego wystąpienia. */
    public static List<Detection<ContractType>> contractTypes(String text) {
        List<Detection<ContractType>> out = new ArrayList<>();
        if (Text.isBlank(text)) {
            return out;
        }
        String folded = Text.fold(text);
        List<int[]> positions = new ArrayList<>();
        for (var e : CONTRACTS.entrySet()) {
            for (Evidence.Hit h : Evidence.findAll(text, e.getValue(), 20)) {
                if (e.getKey() == ContractType.INTERNSHIP && precededByYears(folded, h.start())) {
                    continue; // „3 lata stażu w branży” to doświadczenie, nie staż
                }
                double confidence = switch (e.getKey()) {
                    case B2B -> h.foldedLine().contains("b2b") || h.foldedLine().contains("vat") ? 0.9 : 0.7;
                    case UOP -> folded.substring(h.start(), h.end()).startsWith("etat") ? 0.65 : 0.9;
                    default -> 0.85;
                };
                out.add(new Detection<>(e.getKey(), h.quote(), confidence));
                positions.add(new int[]{out.size() - 1, h.start()});
                break;
            }
        }
        List<Detection<ContractType>> sorted = new ArrayList<>();
        positions.stream().sorted(Comparator.comparingInt(p -> p[1])).forEach(p -> sorted.add(out.get(p[0])));
        return sorted;
    }

    private static boolean precededByYears(String folded, int start) {
        String before = folded.substring(Math.max(0, start - 25), start);
        return YEARS_BEFORE.matcher(before).find();
    }

    // =====================================================================================
    // Poziom stanowiska
    // =====================================================================================

    /**
     * Poziom stanowiska: najpierw z tytułu („Senior”, „Młodszy specjalista”, „Tech Lead”), potem z
     * jawnej wzmianki w treści („poziom mid”), a na końcu z wymaganych lat doświadczenia
     * (0–1 → junior, 2–4 → mid, 5+ → senior).
     */
    public static Optional<Detection<Seniority>> seniority(String title, String text, List<Requirement> requirements) {
        if (!Text.isBlank(title)) {
            String ft = Text.fold(title);
            Map<Seniority, Integer> found = new EnumMap<>(Seniority.class);
            for (var e : TITLE_LEVELS.entrySet()) {
                Matcher m = e.getValue().matcher(ft);
                if (m.find()) {
                    found.put(e.getKey(), m.start());
                }
            }
            if (!found.isEmpty()) {
                // „Senior Tech Lead” → lead; „Junior/Mid” → pierwszy wymieniony poziom
                Seniority best = found.containsKey(Seniority.LEAD) ? Seniority.LEAD
                        : found.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
                boolean ambiguous = found.size() > 1 && !(best == Seniority.LEAD && found.size() == 2
                        && found.containsKey(Seniority.SENIOR));
                return Optional.of(new Detection<>(best, title.strip(), ambiguous ? 0.6 : 0.9));
            }
        }
        if (!Text.isBlank(text)) {
            Optional<Evidence.Hit> explicit = Evidence.find(text, TEXT_LEVEL);
            if (explicit.isPresent()) {
                Matcher m = TEXT_LEVEL.matcher(Text.fold(text));
                if (m.find()) {
                    String level = m.group(1) != null ? m.group(1) : m.group(2);
                    Seniority s = switch (level) {
                        case "junior" -> Seniority.JUNIOR;
                        case "senior" -> Seniority.SENIOR;
                        default -> Seniority.MID;
                    };
                    return Optional.of(new Detection<>(s, explicit.get().quote(), 0.75));
                }
            }
        }
        Integer years = null;
        String yearsQuote = null;
        if (requirements != null) {
            for (Requirement r : requirements) {
                Integer y = r.minYears() != null ? r.minYears() : YearsParser.requiredYears(r.text());
                if (r.mustHave() && y != null && (years == null || y > years)) {
                    years = y;
                    yearsQuote = r.text();
                }
            }
        }
        if (years != null) {
            Seniority s = years <= 1 ? Seniority.JUNIOR : years <= 4 ? Seniority.MID : Seniority.SENIOR;
            String quote = text != null && Evidence.isVerbatim(text, yearsQuote) ? yearsQuote : null;
            return Optional.of(new Detection<>(s, quote, 0.6));
        }
        if (!Text.isBlank(text)) {
            Optional<Evidence.Hit> none = Evidence.find(text, NO_EXPERIENCE);
            if (none.isPresent()) {
                return Optional.of(new Detection<>(Seniority.JUNIOR, none.get().quote(), 0.6));
            }
        }
        return Optional.empty();
    }
}

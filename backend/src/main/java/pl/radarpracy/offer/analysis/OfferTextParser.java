package pl.radarpracy.offer.analysis;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryParser;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryRange;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser struktury oferty (pomysł #13): z wklejonej treści ogłoszenia rozpoznaje stanowisko, firmę,
 * lokalizację, widełki, tryb pracy, formę zatrudnienia, poziom, wymagania i benefity.
 *
 * <p>Działa w pełni offline na jawnych regułach. Każde pole ma pewność 0–1 i — jeśli to możliwe —
 * dosłowny cytat ze źródła; pola poniżej {@link #REVIEW_THRESHOLD} UI oznacza „do sprawdzenia”.</p>
 */
@Component
public class OfferTextParser {

    /** Poniżej tej pewności pole oznaczamy do sprawdzenia. */
    public static final double REVIEW_THRESHOLD = 0.75;
    /** Maksymalna długość analizowanej treści. */
    public static final int MAX_TEXT = 100_000;

    private static final List<String> KEY_FIELDS = List.of(
            "title", "company", "location", "salary", "workMode", "contractTypes", "seniority", "requirements");
    private static final Map<String, Double> WEIGHTS = Map.of(
            "title", 3.0, "company", 2.0, "location", 1.5, "salary", 2.0, "workMode", 1.0, "contractTypes", 1.0,
            "seniority", 0.5, "requirements", 2.0);
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("title", "Stanowisko");
        LABELS.put("company", "Firma");
        LABELS.put("location", "Lokalizacja");
        LABELS.put("salary", "Wynagrodzenie");
        LABELS.put("workMode", "Tryb pracy");
        LABELS.put("contractTypes", "Forma zatrudnienia");
        LABELS.put("seniority", "Poziom");
        LABELS.put("requirements", "Wymagania");
        LABELS.put("benefits", "Benefity");
        LABELS.put("source", "Źródło");
        LABELS.put("expiresAt", "Ważne do");
    }

    private static final Pattern TITLE_LABEL = Evidence.p(
            "^(?:stanowisko|nazwa stanowiska|position|job title|tytul|rola|role)\\s*[:\\-–—]\\s*\\S");
    private static final Pattern COMPANY_LABEL = Evidence.p(
            "^(?:firma|pracodawca|nazwa firmy|company|employer|organizacja|zleceniodawca)\\s*[:\\-–—]\\s*\\S");
    private static final Pattern LOCATION_LABEL = Evidence.p(
            "^(?:lokalizacja|lokalizacje|miejsce pracy|miejscowosc|miasto|location|siedziba|adres)\\s*[:\\-–—]\\s*\\S");
    private static final Pattern LEGAL_FORM = Pattern.compile(
            "((?:[\\p{Lu}\\d][\\p{L}\\d&'.\\-]*[ \\t\\u00a0]+){0,4}[\\p{Lu}\\d][\\p{L}\\d&'.\\-]*?)[ \\t\\u00a0]*,?[ \\t\\u00a0]+"
                    + "((?i:sp\\.\\s?z\\s?o\\.\\s?o\\.|sp\\.\\s?j\\.|sp\\.\\s?k\\.|sp\\.\\s?p\\.|s\\.\\s?a\\.|spółka z o\\.\\s?o\\.|"
                    + "spółka akcyjna|spółka jawna|gmbh|ltd\\.?|limited|inc\\.?|llc|s\\.r\\.o\\.|b\\.v\\.)|SA|AB|AG|AS|Oy|NV)"
                    + "(?![\\p{L}])");
    private static final Pattern ABOUT_COMPANY = Pattern.compile(
            "(?<![\\p{L}])(?iu:o firmie|o pracodawcy|about|dołącz do|dolacz do|join)[ \\t]+((?:[\\p{Lu}\\d][\\p{L}\\d&.\\-]*)(?:[ \\t]+[\\p{Lu}][\\p{L}\\d&.\\-]*){0,3})");
    private static final Pattern TITLE_SEPARATOR = Pattern.compile("\\s+(?:[—–|@]|-{1,2}|at|w firmie)\\s+");
    private static final Pattern ROLE_WORDS = Evidence.p(
            "develop|deweloper|programist|engineer|inzynier|specjalist|specialist|analityk|analyst|tester|\\bqa\\b"
                    + "|architekt|architect|administrator|designer|projektant|konsultant|consultant|manager|menedzer"
                    + "|kierownik|lider|\\blead\\b|koordynator|asystent|assistant|ksiegow|accountant|sprzedawc|handlowiec"
                    + "|przedstawiciel|doradca|opiekun|pracownik|operator|technik|kierowca|magazynier|rekruter|recruiter"
                    + "|scientist|devops|\\bsre\\b|\\bux\\b|\\bui\\b|product owner|scrum master|support|helpdesk|obslug"
                    + "|praca|stazyst|intern|junior|senior|mid\\b|kontroler|controller|ekspert|expert|dyrektor|director"
                    + "|wsparci|grafik|marketing|copywriter|redaktor|nauczyciel|lekarz|pielegniar|ratownik|inspektor");
    private static final Pattern NOISE_TITLE = Evidence.p(
            "^(?:oferta pracy|ogloszenie|ogłoszenie|szczegoly oferty|job offer|job description|opis stanowiska|aplikuj"
                    + "|apply|zapisz|udostepnij|share|nowa oferta|praca|pracodawca|strona glowna|home|menu|oferty pracy)\\W*$");
    private static final Pattern URLISH = Evidence.p("^(?:https?://|www\\.)\\S+$");
    private static final Pattern EXPIRES = Evidence.p(
            "(?:wazn\\w*|aktualn\\w*|aplikuj\\w*|zgloszeni\\w*|cv|termin\\w*(?: skladania)?(?: aplikacji| zgloszen| cv)?"
                    + "|valid|apply|deadline|expires?)\\s*(?:do|until|by|before|:)?\\s*:?\\s*"
                    + "(?:(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})|(\\d{4})-(\\d{2})-(\\d{2})|(\\d{1,2})\\s+(stycznia|lutego|marca"
                    + "|kwietnia|maja|czerwca|lipca|sierpnia|wrzesnia|pazdziernika|listopada|grudnia)\\s+(\\d{4}))");
    private static final List<String> MONTHS = List.of("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
            "lipca", "sierpnia", "wrzesnia", "pazdziernika", "listopada", "grudnia");
    private static final Pattern SALARY_NOT_PAY = Evidence.p(
            "budzet|dofinansow|doplat|oplat|pakiet startow|polecen|bonus za|kaucj|zwrot|karta|wyposazen|dopłat|refund"
                    + "|budget|allowance|voucher|bon\\w* ");
    private static final Pattern SALARY_PAY = Evidence.p(
            "wynagrodz|pensj|stawk|salary|brutto|netto|\\bb2b\\b|\\buop\\b|zarob|placa|pay\\b|rate|widelk|umowa o prace|\\bvat\\b");
    private static final Set<String> GENERIC_COMPANY_WORDS = Set.of(
            "firma", "pracodawca", "company", "employer", "o", "nas", "us", "zespol", "team", "our", "nasz", "nasza");

    private final RequirementExtractor extractor;

    public OfferTextParser(RequirementExtractor extractor) {
        this.extractor = extractor;
    }

    /** Rozpoznanie pól z treści ogłoszenia. {@code url} (opcjonalny) służy do ustalenia źródła. */
    public ParsedOffer parse(String text, String url) {
        return analyze(text, url).build("text", new ArrayList<>());
    }

    /** Rozpoznanie pól; wynik można uzupełnić (np. danymi JSON-LD) przed {@link Builder#build}. */
    Builder analyze(String rawText, String url) {
        String text = rawText == null ? "" : rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        Builder b = new Builder(text);
        List<String> lines = Text.lines(text);

        // --- stanowisko i firma z nagłówka ---
        detectTitleAndCompany(text, lines, b);
        detectCompanyFallbacks(text, lines, b);

        // --- lokalizacja ---
        detectLocation(text, lines, b);

        // --- wynagrodzenie ---
        Optional<SalaryRange> salary = findSalary(text);
        salary.ifPresent(r -> {
            b.salaryMin = r.min();
            b.salaryMax = r.max();
            b.salaryCurrency = r.currency();
            b.salaryPeriod = r.period();
            b.salaryGross = r.gross();
            double conf = r.gross() == null ? 0.7 : 0.85;
            b.field("salary", r.matchedText(), conf, r.matchedText(), "rozpoznane widełki");
        });

        // --- tryb pracy, umowy, poziom ---
        EmploymentTermsDetector.workMode(text).ifPresent(d -> {
            b.workMode = d.value();
            b.field("workMode", workModeLabel(d.value()), d.confidence(), d.quote(), "słowa kluczowe trybu pracy");
        });
        Set<ContractType> contracts = new LinkedHashSet<>();
        List<String> contractQuotes = new ArrayList<>();
        double contractConf = 0;
        for (var d : EmploymentTermsDetector.contractTypes(text)) {
            contracts.add(d.value());
            contractQuotes.add(d.quote());
            contractConf = Math.max(contractConf, d.confidence());
        }
        if (salary.isPresent() && salary.get().contractHint() != null && contracts.add(salary.get().contractHint())) {
            contractConf = Math.max(contractConf, 0.75);
            contractQuotes.add(salary.get().matchedText());
        }
        b.contractTypes = new ArrayList<>(contracts);
        if (!contracts.isEmpty()) {
            b.field("contractTypes", String.join(", ", contracts.stream().map(OfferTextParser::contractLabel).toList()),
                    contractConf, contractQuotes.get(0), "nazwy form zatrudnienia");
        }

        b.requirements = extractor.extract(text);
        if (!b.requirements.isEmpty()) {
            long must = b.requirements.stream().filter(Requirement::mustHave).count();
            boolean fromSection = hasRequirementSection(text);
            b.field("requirements", b.requirements.size() + " (obowiązkowe: " + must + ")", fromSection ? 0.85 : 0.6,
                    null, fromSection ? "sekcja „Wymagania” / „Mile widziane”" : "umiejętności ze słownika (brak sekcji)");
        }

        EmploymentTermsDetector.seniority(b.title, text, b.requirements).ifPresent(d -> {
            b.seniority = d.value();
            b.field("seniority", seniorityLabel(d.value()), d.confidence(), d.quote(),
                    d.confidence() >= 0.9 ? "z tytułu stanowiska" : "z treści / lat doświadczenia");
        });

        b.benefits = OfferSections.benefits(text);
        if (!b.benefits.isEmpty()) {
            b.field("benefits", b.benefits.size() + " pozycji", 0.8, b.benefits.get(0), "sekcja „Oferujemy / Benefity”");
        }

        SourceResolver.fromUrl(url).ifPresent(s -> {
            b.source = s;
            b.url = url.strip();
            b.field("source", s, 0.95, null, "host adresu URL");
        });

        detectExpiry(text, b);

        return b;
    }

    // =====================================================================================
    // Stanowisko i firma
    // =====================================================================================

    private void detectTitleAndCompany(String text, List<String> lines, Builder b) {
        // 1) etykieta „Stanowisko: …”
        for (String line : lines.subList(0, Math.min(lines.size(), 25))) {
            if (TITLE_LABEL.matcher(Text.fold(line)).find()) {
                String value = afterLabel(line);
                b.title = cleanTitle(value);
                b.field("title", b.title, 0.95, line, "etykieta „Stanowisko:”");
                return;
            }
        }
        // 2) nagłówek Markdown „# …” albo pierwsza sensowna linia
        String candidate = null;
        double conf = 0;
        String method = null;
        for (String line : lines.subList(0, Math.min(lines.size(), 8))) {
            if (line.startsWith("# ") || line.startsWith("## ")) {
                candidate = line;
                conf = 0.9;
                method = "nagłówek";
                break;
            }
        }
        if (candidate == null) {
            for (String line : lines.subList(0, Math.min(lines.size(), 12))) {
                if (isTitleCandidate(line)) {
                    candidate = line;
                    method = "pierwsza linia";
                    break;
                }
            }
        }
        if (candidate == null) {
            return;
        }
        String stripped = Evidence.stripDecorations(candidate).replaceAll("[*_]+", "").strip();
        String titlePart = stripped;
        Matcher sep = TITLE_SEPARATOR.matcher(stripped);
        while (sep.find()) {
            String left = stripped.substring(0, sep.start()).strip();
            String right = stripped.substring(sep.end()).strip();
            if (left.length() < 3 || right.isEmpty()) {
                continue;
            }
            if (PolishCities.isCity(firstSegment(right)) || isWorkModeWord(right)) {
                titlePart = left;
                break;
            }
            if (looksLikeCompany(right)) {
                titlePart = left;
                if (b.company == null) {
                    String company = right.replaceAll("[,;].*$", "").strip();
                    b.company = company;
                    b.field("company", company, 0.8, candidate, "nagłówek „Stanowisko — Firma”");
                }
                break;
            }
        }
        b.title = cleanTitle(titlePart);
        if (conf == 0) {
            conf = ROLE_WORDS.matcher(Text.fold(b.title)).find() ? 0.85 : 0.6;
        }
        b.field("title", b.title, conf, candidate, method);
    }

    private boolean isTitleCandidate(String line) {
        String t = Evidence.stripDecorations(line).replaceAll("[*_]+", "").strip();
        String f = Text.fold(t);
        if (t.length() < 3 || t.length() > 140 || URLISH.matcher(f).find() || NOISE_TITLE.matcher(f).find()) {
            return false;
        }
        if (OfferSections.isOtherHeader(line) || OfferSections.benefitHeaderRest(line) != null) {
            return false;
        }
        if (COMPANY_LABEL.matcher(f).find() || LOCATION_LABEL.matcher(f).find()) {
            return false;
        }
        // linia złożona tylko z miasta lub kwoty to nie tytuł
        if (PolishCities.isCity(t) || (SalaryParser.parse(t).isPresent() && !ROLE_WORDS.matcher(f).find())) {
            return false;
        }
        long letters = t.chars().filter(Character::isLetter).count();
        return letters >= 3;
    }

    private void detectCompanyFallbacks(String text, List<String> lines, Builder b) {
        // etykieta „Firma: …” ma pierwszeństwo nawet nad nagłówkiem
        for (String line : lines.subList(0, Math.min(lines.size(), 40))) {
            if (COMPANY_LABEL.matcher(Text.fold(line)).find()) {
                String value = afterLabel(line).replaceAll("\\s*[(|].*$", "").strip();
                if (!value.isEmpty()) {
                    b.company = value;
                    b.field("company", value, 0.95, line, "etykieta „Firma:”");
                    return;
                }
            }
        }
        if (b.company != null) {
            return;
        }
        // nazwa z formą prawną („… sp. z o.o.”, „… S.A.”)
        Matcher m = LEGAL_FORM.matcher(text);
        while (m.find()) {
            String name = trimLeadingNoise(m.group(1)) + " " + m.group(2);
            name = name.strip();
            if (!trimLeadingNoise(m.group(1)).isEmpty() && name.length() >= 4) {
                int lineStart = text.lastIndexOf('\n', m.start()) + 1;
                b.company = name;
                b.field("company", name, 0.85, Evidence.quote(text, Math.max(lineStart, m.start()), m.end()),
                        "nazwa z formą prawną");
                return;
            }
        }
        // druga linia nagłówka: „LogiTech Solutions — Kraków / zdalnie”, „Acme, Warszawa”
        for (String line : lines.subList(Math.min(1, lines.size()), Math.min(lines.size(), 4))) {
            String t = Evidence.stripDecorations(line);
            Matcher sep = Pattern.compile("\\s+[—–|-]\\s+|,\\s+").matcher(t);
            if (sep.find()) {
                String left = t.substring(0, sep.start()).strip();
                String right = t.substring(sep.end()).strip();
                if (looksLikeCompany(left) && (PolishCities.isCity(firstSegment(right)) || isWorkModeWord(right)
                        || !PolishCities.find(right).isEmpty())) {
                    b.company = left;
                    b.field("company", left, 0.65, line, "linia „Firma — miasto”");
                    return;
                }
            }
        }
        // „O firmie Acme…”, „Dołącz do Acme”
        Matcher about = ABOUT_COMPANY.matcher(text);
        while (about.find()) {
            String name = about.group(1).strip();
            if (looksLikeCompany(name) && !GENERIC_COMPANY_WORDS.contains(Text.fold(name))) {
                b.company = name;
                b.field("company", name, 0.6, Evidence.quote(text, about.start(), about.end()), "„o firmie …” / „dołącz do …”");
                return;
            }
        }
    }

    private static String trimLeadingNoise(String name) {
        String n = name.strip();
        // „W Acme sp. z o.o.” → „Acme”
        return n.replaceFirst("^(?:(?:W|We|Z|Do|Dla|Firma|Spółka|Pracodawca|Company|Join|Dołącz)\\s+)+", "");
    }

    private boolean looksLikeCompany(String s) {
        String t = s.strip();
        if (t.length() < 2 || t.length() > 60) {
            return false;
        }
        String f = Text.fold(t);
        if (ROLE_WORDS.matcher(f).find() && !LEGAL_FORM.matcher(t).find()) {
            return false;
        }
        if (PolishCities.isCity(firstSegment(t)) || isWorkModeWord(t) || SalaryParser.parse(t).isPresent()) {
            return false;
        }
        String[] words = t.split("\\s+");
        if (words.length > 6) {
            return false;
        }
        return Character.isUpperCase(t.codePointAt(0)) || Character.isDigit(t.charAt(0));
    }

    private static String firstSegment(String s) {
        return s.split("[/,(|]")[0].strip();
    }

    private static boolean isWorkModeWord(String s) {
        String f = Text.fold(s).strip();
        return f.matches("^(?:100% )?(?:zdaln\\w*|remote|hybryd\\w*|hybrid|stacjonarn\\w*|on-?site|praca zdalna).*");
    }

    private static String afterLabel(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) {
            Matcher m = Pattern.compile("\\s[-–—]\\s").matcher(line);
            idx = m.find() ? m.end() - 1 : -1;
        }
        return Evidence.stripDecorations(idx >= 0 ? line.substring(idx + 1) : line).replaceAll("[*_]+", "").strip();
    }

    private static String cleanTitle(String t) {
        String s = t.replaceAll("^#+\\s*", "").replaceAll("[*_]+", "").strip();
        s = s.replaceAll("\\s*\\((?:k/m|m/k|m/f|f/m|m/f/d|f/m/d|k/m/n|m/k/n|m/w/d)\\)\\s*", " ").strip();
        s = s.replaceAll("[\\s,;:–—-]+$", "");
        return Text.truncate(s, 200);
    }

    // =====================================================================================
    // Lokalizacja
    // =====================================================================================

    private void detectLocation(String text, List<String> lines, Builder b) {
        for (String line : lines.subList(0, Math.min(lines.size(), 40))) {
            if (LOCATION_LABEL.matcher(Text.fold(line)).find()) {
                String value = afterLabel(line);
                List<PolishCities.CityHit> cities = PolishCities.find(value);
                String loc = cities.isEmpty() ? value.replaceAll("\\s*\\(.*$", "").strip() : joinCities(cities);
                if (!loc.isEmpty()) {
                    b.location = Text.truncate(loc, 120);
                    b.field("location", b.location, 0.95, line, "etykieta „Lokalizacja:”");
                    return;
                }
            }
        }
        String head = String.join("\n", lines.subList(0, Math.min(lines.size(), 10)));
        List<PolishCities.CityHit> headCities = PolishCities.find(head);
        if (!headCities.isEmpty()) {
            b.location = joinCities(headCities);
            PolishCities.CityHit first = headCities.get(0);
            b.field("location", b.location, 0.8, Evidence.quote(head, first.start(), first.end()), "miasto w nagłówku ogłoszenia");
            return;
        }
        List<PolishCities.CityHit> all = PolishCities.find(text);
        if (!all.isEmpty()) {
            PolishCities.CityHit first = all.get(0);
            b.location = first.city();
            b.field("location", b.location, 0.6, Evidence.quote(text, first.start(), first.end()), "miasto w treści");
        }
    }

    private static String joinCities(List<PolishCities.CityHit> hits) {
        Set<String> names = new LinkedHashSet<>();
        for (var h : hits) {
            names.add(h.city());
            if (names.size() == 3) {
                break;
            }
        }
        return String.join(", ", names);
    }

    // =====================================================================================
    // Wynagrodzenie, daty
    // =====================================================================================

    /**
     * Widełki wynagrodzenia — rdzeniowy {@link SalaryParser}, ale z pominięciem kwot, które nie są
     * pensją (budżet szkoleniowy, dofinansowanie, opłata).
     */
    static Optional<SalaryRange> findSalary(String text) {
        int from = 0;
        for (int attempt = 0; attempt < 6 && from < text.length(); attempt++) {
            String rest = text.substring(from);
            Optional<SalaryRange> r = SalaryParser.parse(rest);
            if (r.isEmpty()) {
                return Optional.empty();
            }
            int idx = rest.indexOf(r.get().matchedText());
            if (idx < 0) {
                return r;
            }
            String line = Evidence.lineAround(Text.fold(rest), idx, idx + r.get().matchedText().length());
            boolean notPay = SALARY_NOT_PAY.matcher(line).find() && !SALARY_PAY.matcher(line).find();
            if (!notPay) {
                return r;
            }
            from += idx + r.get().matchedText().length();
        }
        return Optional.empty();
    }

    private void detectExpiry(String text, Builder b) {
        Matcher m = EXPIRES.matcher(Text.fold(text));
        if (!m.find()) {
            return;
        }
        try {
            LocalDate date;
            if (m.group(1) != null) {
                date = LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            } else if (m.group(4) != null) {
                date = LocalDate.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
            } else {
                date = LocalDate.of(Integer.parseInt(m.group(9)), MONTHS.indexOf(m.group(8)) + 1, Integer.parseInt(m.group(7)));
            }
            b.expiresAt = date;
            b.field("expiresAt", date.toString(), 0.8, Evidence.quote(text, m.start(), m.end()), "data ważności");
        } catch (DateTimeException | NumberFormatException e) {
            // niepoprawna data — pomijamy
        }
    }

    private static boolean hasRequirementSection(String text) {
        for (String line : Text.lines(text)) {
            String f = Text.fold(Evidence.stripDecorations(line));
            if (f.length() < 60 && f.matches("^(?:wymagania|wymagamy|oczekujemy|czego oczekujemy|nasze oczekiwania"
                    + "|requirements|must have|kogo szukamy|your profile|twoj profil|who you are|mile widzian|nice to have).*")) {
                return true;
            }
        }
        return false;
    }

    static String workModeLabel(WorkMode m) {
        return switch (m) {
            case REMOTE -> "zdalnie";
            case HYBRID -> "hybrydowo";
            case ONSITE -> "stacjonarnie";
        };
    }

    static String contractLabel(ContractType c) {
        return switch (c) {
            case UOP -> "umowa o pracę";
            case B2B -> "B2B";
            case UZ -> "umowa zlecenie";
            case UOD -> "umowa o dzieło";
            case INTERNSHIP -> "staż";
        };
    }

    static String seniorityLabel(Seniority s) {
        return switch (s) {
            case INTERN -> "stażysta";
            case JUNIOR -> "junior";
            case MID -> "mid";
            case SENIOR -> "senior";
            case LEAD -> "lead";
        };
    }

    // =====================================================================================
    // Złożenie wyniku
    // =====================================================================================

    /** Zbiera rozpoznane wartości; pola nadpisywane przez import JSON-LD. */
    static final class Builder {
        final String text;
        String title;
        String company;
        String location;
        String url;
        String source;
        String description;
        WorkMode workMode;
        List<ContractType> contractTypes = new ArrayList<>();
        Seniority seniority;
        Integer salaryMin;
        Integer salaryMax;
        String salaryCurrency;
        SalaryPeriod salaryPeriod;
        Boolean salaryGross;
        List<Requirement> requirements = new ArrayList<>();
        List<String> benefits = new ArrayList<>();
        LocalDate publishedAt;
        LocalDate expiresAt;
        final Map<String, ParsedField> fields = new LinkedHashMap<>();
        /** Dodatkowe źródła cytatów (np. surowe dane JSON-LD strony). */
        final List<String> extraSources = new ArrayList<>();

        Builder(String text) {
            this.text = text;
            this.description = text;
        }

        void field(String name, String value, double confidence, String evidence, String method) {
            String ev = evidence == null ? null : evidence.strip();
            // zasada nr 2: cytat musi być dosłownym fragmentem źródła — inaczej go nie pokazujemy
            if (ev != null && !Evidence.isVerbatim(text, ev)
                    && extraSources.stream().noneMatch(src -> Evidence.isVerbatim(src, evidence.strip()))) {
                ev = null;
            }
            double c = Math.round(confidence * 100) / 100.0;
            fields.put(name, new ParsedField(name, LABELS.getOrDefault(name, name), value, c, ev, method,
                    c < REVIEW_THRESHOLD));
        }

        ParsedOffer build(String origin, List<String> warnings) {
            if (location == null && workMode == WorkMode.REMOTE) {
                location = "zdalnie";
                field("location", "zdalnie", 0.7, null, "praca zdalna bez miasta");
            }
            for (String key : KEY_FIELDS) {
                if (!fields.containsKey(key)) {
                    fields.put(key, new ParsedField(key, LABELS.get(key), null, 0, null, "nie znaleziono", true));
                }
            }
            if (salaryMin == null && salaryMax == null && !warnings.contains(NO_SALARY_WARNING)) {
                warnings.add(NO_SALARY_WARNING);
            }
            OfferDraft draft = new OfferDraft(title, company, location, url, source, description, workMode,
                    List.copyOf(contractTypes), seniority, salaryMin, salaryMax, salaryCurrency == null ? "PLN" : salaryCurrency,
                    salaryPeriod, salaryGross, List.copyOf(requirements), List.copyOf(benefits), publishedAt, expiresAt);
            List<ParsedField> list = new ArrayList<>();
            LABELS.keySet().forEach(k -> {
                if (fields.containsKey(k)) {
                    list.add(fields.get(k));
                }
            });
            List<String> recognized = list.stream().filter(f -> f.value() != null).map(ParsedField::field).toList();
            List<String> toReview = list.stream().filter(ParsedField::review).map(ParsedField::field).toList();
            double sum = 0;
            double weights = 0;
            for (String key : KEY_FIELDS) {
                double w = WEIGHTS.get(key);
                sum += w * fields.get(key).confidence();
                weights += w;
            }
            double overall = Math.round(sum / weights * 100) / 100.0;
            return new ParsedOffer(draft, list, recognized, toReview, overall, REVIEW_THRESHOLD, origin, List.copyOf(warnings));
        }
    }

    static final String NO_SALARY_WARNING = "Ogłoszenie nie podaje widełek — warto o nie zapytać przed rozmową.";
}

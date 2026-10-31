package pl.radarpracy.profile.cv;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wykrywanie zbędnych danych wrażliwych w CV (pomysł #8).
 *
 * <p>Reguły działają na tekście złożonym ({@link Text#fold}: małe litery, bez polskich znaków),
 * a cytat jest wycinany z oryginału w tych samych pozycjach — zawsze jest dosłownym fragmentem CV.
 * PESEL i numer dowodu osobistego bez słowa kluczowego zgłaszamy tylko, gdy przechodzą kontrolę
 * sumy, co ogranicza fałszywe alarmy (np. numery telefonów).</p>
 *
 * <p>Podstawa: art. 22¹ § 1 Kodeksu pracy określa dane, których pracodawca żąda od kandydata (imię
 * i nazwisko, data urodzenia, wskazane przez kandydata dane kontaktowe, a gdy to niezbędne —
 * wykształcenie, kwalifikacje i przebieg zatrudnienia); adres zamieszkania, PESEL i dane dzieci
 * pracodawca zbiera dopiero od pracownika (art. 22¹ § 3 KP).</p>
 */
@Service
public class SensitiveDataDetector {

    /** Waga znaleziska: wysoka (usuń koniecznie), średnia (zbędne), informacyjna (decyzja kandydata). */
    public static final String HIGH = "high";
    public static final String MEDIUM = "medium";
    public static final String INFO = "info";

    private final ProfileService profiles;

    public SensitiveDataDetector(ProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * Znalezisko.
     *
     * @param category   kod kategorii, np. „PESEL”, „BIRTH_DATE”
     * @param label      nazwa kategorii po polsku
     * @param severity   high / medium / info
     * @param quote      dosłowny fragment CV
     * @param why        dlaczego pracodawca tego nie potrzebuje
     * @param suggestion co zrobić
     */
    public record SensitiveFinding(String category, String label, String severity, String quote, String why,
                                   String suggestion) {
    }

    public record SensitiveReport(Long cvId, String cvName, List<SensitiveFinding> findings, String summary) {
    }

    private record Category(String code, String label, String severity, String why, String suggestion) {
    }

    private record Rule(Category category, Pattern pattern) {
    }

    private record Hit(Category category, int start, int end) {
    }

    // ------------------------------------------------------------------ kategorie

    static final Category PESEL = new Category("PESEL", "Numer PESEL", HIGH,
            "PESEL nie jest potrzebny w rekrutacji — pracodawca może go żądać dopiero od zatrudnionego pracownika "
                    + "(art. 22¹ § 3 KP). Numer w CV, które krąży po skrzynkach i portalach, ułatwia kradzież tożsamości.",
            "Usuń numer PESEL z CV.");
    static final Category ID_DOCUMENT = new Category("ID_DOCUMENT", "Numer dowodu osobistego lub paszportu", HIGH,
            "Numeru dokumentu tożsamości nie ma wśród danych, których pracodawca może żądać od kandydata "
                    + "(art. 22¹ § 1 KP), a jego ujawnienie ułatwia wyłudzenia na Twoje dane.",
            "Usuń numer dokumentu z CV.");
    static final Category BIRTH_DATE = new Category("BIRTH_DATE", "Data urodzenia lub wiek", MEDIUM,
            "Pracodawca może poprosić o datę urodzenia (art. 22¹ § 1 KP), ale nie musisz podawać jej w CV "
                    + "z własnej inicjatywy — nie mówi nic o kompetencjach, a może sprzyjać dyskryminacji ze względu "
                    + "na wiek.",
            "Usuń datę urodzenia lub wiek; podasz je, jeśli pracodawca o nie poprosi.");
    static final Category ADDRESS = new Category("ADDRESS", "Pełny adres zamieszkania", MEDIUM,
            "Dane kontaktowe wskazujesz samodzielnie (art. 22¹ § 1 pkt 3 KP) — do kontaktu wystarczą e-mail "
                    + "i telefon. Adres zamieszkania pracodawca zbiera dopiero od pracownika (art. 22¹ § 3 KP).",
            "Zostaw samo miasto (np. „Warszawa”) — to wystarczy do oceny dojazdu.");
    static final Category MARITAL_STATUS = new Category("MARITAL_STATUS", "Stan cywilny", MEDIUM,
            "Stan cywilny nie ma związku z kwalifikacjami i nie należy do danych z art. 22¹ § 1 KP; "
                    + "może prowadzić do nieuczciwej oceny kandydatury.",
            "Usuń informację o stanie cywilnym.");
    static final Category CHILDREN = new Category("CHILDREN", "Dzieci i sytuacja rodzinna", MEDIUM,
            "Informacja o dzieciach nie dotyczy kwalifikacji. Dane dzieci pracodawca może zbierać dopiero od "
                    + "pracownika i tylko wtedy, gdy korzysta on ze szczególnych uprawnień (art. 22¹ § 3 KP).",
            "Usuń informację o dzieciach i sytuacji rodzinnej.");
    static final Category PHOTO = new Category("PHOTO", "Zdjęcie", MEDIUM,
            "Zdjęcia nie ma wśród danych z art. 22¹ § 1 KP. Nie mówi nic o kompetencjach, a może prowadzić "
                    + "do ocen opartych na wyglądzie, wieku lub płci; systemy ATS go nie odczytują.",
            "Usuń zdjęcie i wzmianki o nim — chyba że to Twoja świadoma decyzja (np. branża, w której wizerunek "
                    + "ma znaczenie).");
    static final Category RELIGION = new Category("RELIGION", "Wyznanie", MEDIUM,
            "Przekonania religijne to dane szczególnej kategorii (art. 9 RODO) — pracodawca nie potrzebuje ich "
                    + "do oceny kwalifikacji.",
            "Usuń informację o wyznaniu.");
    static final Category HEALTH = new Category("HEALTH", "Zdrowie lub niepełnosprawność", INFO,
            "Dane o zdrowiu to szczególna kategoria danych (art. 9 RODO) i nie są potrzebne w CV. Informacja "
                    + "o orzeczeniu o niepełnosprawności to Twoja decyzja — bywa korzystna (np. gdy potrzebujesz "
                    + "dostosowania stanowiska albo pracodawca szuka osób z orzeczeniem), ale nie jest wymagana.",
            "Zostaw tylko, jeśli świadomie chcesz o tym poinformować; ogólne „dobry stan zdrowia” możesz usunąć.");
    static final Category PARENTS = new Category("PARENTS", "Dane rodziców lub nazwisko rodowe", MEDIUM,
            "Imiona rodziców i nazwisko rodowe to relikt dawnych wzorów CV — nie ma ich wśród danych "
                    + "z art. 22¹ § 1 KP.",
            "Usuń imiona rodziców i nazwisko rodowe.");
    static final Category NATIONALITY = new Category("NATIONALITY", "Narodowość lub obywatelstwo", INFO,
            "Narodowość nie jest potrzebna do oceny kwalifikacji. Przydatna bywa jedynie informacja o prawie "
                    + "do pracy, gdy rekruter mógłby mieć wątpliwości.",
            "Zamiast narodowości napisz, jeśli trzeba: „Mam prawo do pracy w Polsce”.");

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(HIGH, 0, MEDIUM, 1, INFO, 2);

    // ------------------------------------------------------------------ reguły (na tekście złożonym)

    private static final String L = "\\p{L}";
    private static final String CLAUSE = "[^\\n|;]";

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE);
    }

    private static final List<Rule> RULES = List.of(
            new Rule(PESEL, p("\\bpesel\\b\\s*(?:nr\\.?|numer)?\\s*[:\\-–]?\\s*\\d[\\d ]{9,14}\\d")),
            new Rule(ID_DOCUMENT, p("\\b(?:(?:seria\\s+i\\s+)?(?:nr|numer)\\.?\\s+dowodu(?:\\s+osobistego)?"
                    + "|dowod\\s+osobisty(?:\\s+(?:nr|numer)\\.?)?)\\s*[:\\-–]?\\s*[a-z]{3}\\s?\\d{6}")),
            new Rule(ID_DOCUMENT, p("\\b(?:paszport|passport)(?:\\s+(?:nr|numer|no|number)\\.?)?\\s*[:\\-–]?\\s*"
                    + "[a-z]{2}\\s?\\d{7}")),
            new Rule(BIRTH_DATE, p("\\b(?:data\\s+(?:i\\s+miejsce\\s+)?urodzenia|date\\s+of\\s+birth|urodzon[aey]"
                    + "|dob|born)\\b" + CLAUSE + "{0,12}?\\d" + CLAUSE + "{0,24}")),
            new Rule(BIRTH_DATE, p("\\bur\\.\\s*\\d{1,2}[./\\- ]\\d{1,2}[./\\- ](?:19|20)?\\d{2}")),
            // „mam 29 lat.” — ale nie „mam 10 lat doświadczenia”
            new Rule(BIRTH_DATE, p("\\b(?:wiek|age)\\s*[:\\-–]\\s*\\d{2}\\b|\\bmam\\s+\\d{2}\\s+lata?(?=\\s*(?:[.,;|)]|$))"
                    + "|\\b\\d{2}\\s+years\\s+old\\b|\\b\\d{2}\\s+lat\\s+wieku")),
            new Rule(MARITAL_STATUS, p("\\b(?:stan\\s+cywilny|marital\\s+status)\\s*[:\\-–]?\\s*[\\p{L} ]{0,25}" + L)),
            new Rule(MARITAL_STATUS, p("\\b(?:panna|kawaler|zamezna|zonaty|mezatka|rozwiedzion[aey]|rozwodnik"
                    + "|wdowa|wdowiec|w\\s+zwiazku\\s+malzenskim|married|divorced|widowed)\\b")),
            new Rule(CHILDREN, p("\\b(?:mam|posiadam|matka|ojciec|mama|tata|rodzic)\\s+(?:" + L + "+\\s+){0,2}"
                    + "(?:dzieci|dziecka|dziecko|syna|synow|corke|corki|corek|dwojke|trojke|dwojki|trojki)\\b")),
            new Rule(CHILDREN, p("\\b(?:jedno|dwoje|troje|czworo|piecioro|\\d)\\s+dzieci\\b|\\bliczba\\s+dzieci\\b"
                    + CLAUSE + "{0,10}|\\bdzieci\\s*:\\s*\\d|\\bbezdzietn" + L + "*"
                    + "|\\b(?:mother|father|parent)\\s+of\\s+(?:\\w+\\s+)?(?:children|kids|sons?|daughters?)\\b"
                    + "|\\bnumber\\s+of\\s+children\\b" + CLAUSE + "{0,10}")),
            new Rule(PHOTO, p("\\[\\s*(?:zdjecie|foto|photo|fotografia)\\s*]"
                    + "|^\\s*(?:zdjecie|foto|photo|fotografia)\\s*[:\\-–]" + CLAUSE + "{0,30}"
                    + "|\\b(?:zalaczam|w\\s+zalaczeniu|dolaczam|ponizej|powyzej)\\s+(?:" + L + "+\\s+)?zdjeci" + L + "*"
                    + "|\\bmoje\\s+zdjecie\\b|\\bzdjecie\\s+(?:kandydata|aktualne|legitymacyjne|paszportowe)\\b"
                    + "|\\bphoto\\s+(?:attached|below|above)\\b")),
            new Rule(RELIGION, p("\\b(?:wyznanie|religia|religion)\\s*:\\s*" + CLAUSE + "{0,30}"
                    + "|\\b(?:rzymskokatolick" + L + "*|katolik|katoliczka|ewangelik|ewangeliczka|prawoslawn" + L + "*"
                    + "|muzulman" + L + "*|ateist" + L + "*|buddyst" + L + "*|swiadek\\s+jehowy"
                    + "|catholic|muslim|atheist|buddhist)\\b")),
            new Rule(HEALTH, p("\\b(?:stan\\s+zdrowia|zdrowie\\s*:|health(?:\\s+status)?\\s*:|disability\\s*:)"
                    + CLAUSE + "{0,30}"
                    + "|\\borzeczeni" + L + "*\\s+o\\s+(?:" + L + "+\\s+){0,3}niepelnosprawnosc" + L + "*" + CLAUSE
                    + "{0,30}"
                    + "|\\b(?:lekk|umiarkowan|znaczn)" + L + "*\\s+stop(?:ien|ni" + L + "*)\\b"
                    + "(?:\\s+niepelnosprawnosci)?"
                    + "|\\bstopien\\s+niepelnosprawnosci" + CLAUSE + "{0,20}"
                    + "|\\b(?:jestem|posiadam\\s+status)\\s+(?:osoba\\s+)?(?:z\\s+)?niepelnosprawn" + L + "*"
                    + "|\\bgrupa\\s+inwalidzk" + L + "*")),
            new Rule(PARENTS, p("\\bimi(?:e|ona)\\s+(?:ojca|matki|rodzicow)\\b" + CLAUSE + "{0,30}"
                    + "|\\bnazwisko\\s+rodowe\\b" + CLAUSE + "{0,30}"
                    + "|\\b(?:father'?s|mother'?s)\\s+name\\b" + CLAUSE + "{0,30}"
                    + "|\\bmaiden\\s+name\\b" + CLAUSE + "{0,30}")),
            new Rule(NATIONALITY, p("\\b(?:narodowosc|obywatelstwo|nationality|citizenship)\\b\\s*:?\\s*"
                    + "[\\p{L} \\-]{0,25}" + L)),
            new Rule(ADDRESS, p("\\b(?:adres(?:\\s+zamieszkania)?|address|home\\s+address)\\s*:\\s*[^\\n|;@]*?\\d"
                    + "[^\\n|;@]*")),
            new Rule(ADDRESS, p("(?<![\\p{L}\\p{N}.@/])(?:ul\\.|ulica\\b|al\\.|aleja\\b|aleje\\b|os\\.|osiedle\\b"
                    + "|pl\\.|plac\\b)\\s*" + L + "[\\p{L}.\\- ]{1,40}?\\s\\d+[a-z]?"
                    + "(?:\\s?/\\s?\\d+[a-z]?)?(?:\\s*(?:m\\.?|lok\\.?)\\s*\\d+)?"
                    + "(?:,?\\s*\\d{2}-\\d{3}(?:\\s+" + L + "+(?:[\\- ]" + L + "+)?)?)?")));

    /** 11 cyfr bez słowa kluczowego — zgłaszane tylko, gdy to poprawny PESEL. */
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("(?<![\\d+])\\d{11}(?!\\d)");
    /** Seria i numer dowodu osobistego (3 litery + 6 cyfr) — zgłaszane tylko z poprawną cyfrą kontrolną. */
    private static final Pattern ID_CARD = Pattern.compile("(?<![A-Za-z0-9])([A-Z]{3})\\s?(\\d{6})(?![A-Za-z0-9])");

    public SensitiveReport detect(Long cvId) {
        CvVariant cv = profiles.cv(cvId);
        SensitiveReport r = detect(cv.getContent());
        return new SensitiveReport(cv.getId(), cv.getName(), r.findings(), r.summary());
    }

    /** Analiza dowolnego tekstu CV (logika bez dostępu do bazy). */
    public SensitiveReport detect(String cvText) {
        String text = Text.nullToEmpty(cvText);
        String folded = Text.fold(text);
        List<Hit> hits = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(folded);
            while (m.find()) {
                hits.add(new Hit(rule.category(), m.start(), m.end()));
            }
        }
        Matcher d = ELEVEN_DIGITS.matcher(text);
        while (d.find()) {
            if (isValidPesel(d.group())) {
                hits.add(new Hit(PESEL, d.start(), d.end()));
            }
        }
        Matcher id = ID_CARD.matcher(text);
        while (id.find()) {
            if (isValidIdCard(id.group(1) + id.group(2))) {
                hits.add(new Hit(ID_DOCUMENT, id.start(), id.end()));
            }
        }
        List<SensitiveFinding> findings = new ArrayList<>();
        for (Hit h : merge(hits)) {
            String quote = trimQuote(text.substring(h.start(), h.end()));
            if (quote.isEmpty() || !text.contains(quote)) {
                continue;
            }
            Category c = h.category();
            findings.add(new SensitiveFinding(c.code(), c.label(), c.severity(), quote, c.why(), c.suggestion()));
        }
        findings.sort(Comparator.comparingInt(f -> SEVERITY_ORDER.get(f.severity())));
        return new SensitiveReport(null, null, findings, summary(findings));
    }

    /** Scala nakładające się trafienia tej samej kategorii (zostaje najdłuższe), w kolejności występowania. */
    private static List<Hit> merge(List<Hit> hits) {
        List<Hit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingInt(Hit::start).thenComparing(h -> -(h.end() - h.start())));
        List<Hit> out = new ArrayList<>();
        for (Hit h : sorted) {
            boolean covered = false;
            for (int i = 0; i < out.size(); i++) {
                Hit o = out.get(i);
                if (o.category() == h.category() && h.start() < o.end() && o.start() < h.end()) {
                    out.set(i, new Hit(o.category(), Math.min(o.start(), h.start()), Math.max(o.end(), h.end())));
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                out.add(h);
            }
        }
        return out;
    }

    private static String trimQuote(String s) {
        String t = s.strip();
        while (!t.isEmpty() && ",;|-–—:".indexOf(t.charAt(t.length() - 1)) >= 0) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return t;
    }

    private static String summary(List<SensitiveFinding> findings) {
        if (findings.isEmpty()) {
            return "Nie znaleziono zbędnych danych wrażliwych.";
        }
        long high = findings.stream().filter(f -> HIGH.equals(f.severity())).count();
        int n = findings.size();
        return "Znaleziono " + n + " " + Plural.of(n, "fragment", "fragmenty", "fragmentów")
                + " z danymi, których pracodawca nie potrzebuje"
                + (high > 0 ? " — w tym " + high + " " + Plural.of((int) high, "szczególnie ryzykowny",
                "szczególnie ryzykowne", "szczególnie ryzykownych") + " (usuń koniecznie)" : "") + ".";
    }

    /**
     * Walidacja numeru PESEL: suma kontrolna (wagi 1-3-7-9-1-3-7-9-1-3) i poprawna data urodzenia
     * (miesiąc +80 dla XIX w., +20 dla XXI w., +40 dla XXII w., +60 dla XXIII w.).
     */
    public static boolean isValidPesel(String pesel) {
        if (pesel == null || !pesel.matches("\\d{11}")) {
            return false;
        }
        int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (pesel.charAt(i) - '0') * weights[i];
        }
        if ((10 - sum % 10) % 10 != pesel.charAt(10) - '0') {
            return false;
        }
        int yy = Integer.parseInt(pesel.substring(0, 2));
        int mm = Integer.parseInt(pesel.substring(2, 4));
        int dd = Integer.parseInt(pesel.substring(4, 6));
        int century = switch (mm / 20) {
            case 0 -> 1900;
            case 1 -> 2000;
            case 2 -> 2100;
            case 3 -> 2200;
            case 4 -> 1800;
            default -> -1;
        };
        if (century < 0) {
            return false;
        }
        try {
            LocalDate.of(century + yy, mm % 20, dd);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** Walidacja serii i numeru dowodu osobistego: wagi 7-3-1-9-7-3-1-7-3, litery A=10 … Z=35, suma mod 10 = 0. */
    public static boolean isValidIdCard(String id) {
        if (id == null || !id.matches("[A-Z]{3}\\d{6}")) {
            return false;
        }
        int[] weights = {7, 3, 1, 9, 7, 3, 1, 7, 3};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = id.charAt(i);
            int value = Character.isLetter(c) ? c - 'A' + 10 : c - '0';
            sum += value * weights[i];
        }
        return sum % 10 == 0;
    }
}

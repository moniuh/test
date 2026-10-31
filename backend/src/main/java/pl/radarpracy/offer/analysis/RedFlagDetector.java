package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.YearsParser;
import pl.radarpracy.offer.analysis.RedFlag.Severity;
import pl.radarpracy.salary.SalaryParser;
import pl.radarpracy.salary.SalaryRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detektor czerwonych flag w ogłoszeniu (pomysł #18). Każda flaga ma kod, wagę, uzasadnienie
 * (z odwołaniem do Kodeksu pracy, gdzie to zasadne) i dosłowny cytat z ogłoszenia.
 *
 * <p>Reguły są jawne (wyrażenia regularne na tekście bez polskich znaków) i działają offline. To
 * podpowiedzi — kandydat sam ocenia, czy sygnał jest istotny.</p>
 */
public final class RedFlagDetector {

    /** Widełki uznajemy za bardzo szerokie, gdy górna granica ≥ tyle razy dolna. */
    static final double WIDE_RANGE_RATIO = 1.8;
    /** Junior: od tylu lat wymaganego doświadczenia wymagania uznajemy za wygórowane. */
    static final int JUNIOR_MAX_YEARS = 3;
    /** Junior: od tylu wymagań obowiązkowych lista jest wygórowana. */
    static final int JUNIOR_MAX_MUST = 10;

    private static final Pattern VAGUE_PAY = Evidence.p(
            "atrakcyjn\\w* wynagrodz\\w*|konkurencyjn\\w* wynagrodz\\w*|adekwatn\\w* wynagrodz\\w*|wynagrodzeni\\w* adekwatn\\w*"
                    + "|wynagrodzeni\\w* do uzgodnienia|godziw\\w* wynagrodz\\w*|competitive (?:salary|pay|compensation)"
                    + "|attractive (?:salary|pay)");
    private static final Pattern UOD = Evidence.p(
            "umow\\w* o dzielo|umow\\w*[- ]dzielo|contract for (?:a )?specific (?:work|task)|specific[- ]task contract");
    private static final Pattern UOD_NEGATION = Evidence.p("nie oferujemy|nie stosujemy|zadnych umow|bez umow");
    private static final Pattern CONTINUOUS_WORK = Evidence.p(
            "pel\\w* etat|peln\\w* wymiar|stal\\w* (?:prac|wspolprac|zatrudn)|grafik\\w*|\\bzmian\\w*|godzin\\w* pracy"
                    + "|od poniedzialku do piatku|\\d+\\s*h\\s*dziennie|8 godzin|w godzinach|obslug\\w* klient|dyspozycyjn\\w*"
                    + "|nadgodzin\\w*|okres\\w* probn\\w*|full[- ]time|awans\\w*|na stale|dlugotermin\\w*|specjalist\\w*");
    private static final Pattern UNPAID_TRIAL = Evidence.p(
            "(?:bezplatn|nieodplatn|darmow|nieplatn)\\w*\\s+(?:\\w+\\s+){0,2}(?:okres\\w* probn|dni\\w* probn|dzien\\w* probn"
                    + "|tydzien\\w* probn|prac\\w* (?:na )?prob)"
                    + "|(?:okres\\w* probn\\w*|dni\\w* probn\\w*|dzien\\w* probn\\w*)\\W+(?:\\w+\\W+){0,4}?(?:bez wynagrodzenia"
                    + "|bezplatn\\w*|nieodplatn\\w*|nieplatn\\w*|bez zaplaty|za darmo)"
                    + "|unpaid (?:trial|probation)|trial (?:day|days|period|week) (?:is |are )?unpaid");
    private static final Pattern TRIAL_DAYS = Evidence.p(
            "\\bdni\\w* probn\\w*|\\bdzien\\w* probn\\w*|\\bprobn\\w* dzien\\w*|\\btrial days?\\b");
    private static final Pattern UNPAID_INTERNSHIP = Evidence.p(
            "(?:bezplatn|nieodplatn|nieplatn)\\w*\\s+(?:\\w+\\s+){0,1}(?:staz\\w*|praktyk\\w*)|unpaid internship"
                    + "|(?:staz\\w*|praktyk\\w*)\\s+(?:\\w+\\s+){0,2}(?:bezplatn|nieodplatn|nieplatn)\\w*");
    private static final Pattern FAMILY = Evidence.p(
            "rodzinn\\w* atmosfer\\w*|jak (?:w )?rodzin\\w*|jestesmy (?:jak )?rodzin\\w*|we are (?:like )?(?:a )?family"
                    + "|family atmosphere|family[- ]like");
    private static final Pattern YOUNG_TEAM = Evidence.p(
            "mlod\\w*,?\\s+(?:i\\s+)?dynamiczn\\w*\\s+zesp\\w*|dynamiczn\\w*,?\\s+(?:i\\s+)?mlod\\w*\\s+zesp\\w*"
                    + "|\\bmlod\\w* zesp\\w*|young (?:and )?dynamic team|\\byoung team");
    private static final Pattern AVAILABILITY = Evidence.p(
            "24\\s*/\\s*7|24h\\s*/\\s*(?:dobe|7)|calodobow\\w*\\s+dyspozycyjn\\w*|pel\\w* dyspozycyjnosc\\w*"
                    + "|dyspozycyjn\\w*\\s+(?:24|calodobow\\w*|w kazdej chwili|o kazdej porze)|available 24/7"
                    + "|full availability|always available|on[- ]call 24");
    private static final Pattern OVERTIME = Evidence.p(
            "nadgodzin\\w*|w weekend\\w*|weekendow\\w*|w soboty|w niedziel\\w*|w dni wolne|po godzinach|\\bovertime\\b"
                    + "|\\bweekends?\\b");
    private static final Pattern OVERTIME_OK = Evidence.p(
            "nie pracujemy w weekend|bez nadgodzin|no overtime|weekendy wolne|wolne weekendy|wolne soboty|bez weekendow");
    private static final Pattern OVERTIME_PAID = Evidence.p(
            "platn\\w*|wynagradzan\\w*|dodatk\\w*|\\d{2,3}\\s*%|odbior\\w*|\\bpaid\\b|rozliczan\\w*");
    private static final Pattern STRESS = Evidence.p(
            "odpornosc\\w* na stres\\w*|odporn\\w* na stres\\w*|odpornosc\\w* psychiczn\\w*|prac\\w* pod presj\\w*"
                    + "|pod presja czasu|w stresie|stresujac\\w*|stress[- ]resistan\\w*|resistant to stress|resilience to stress"
                    + "|work(?:ing)? under pressure|high[- ]pressure environment");
    private static final Pattern RESULTS_PAY = Evidence.p(
            "uzaleznion\\w* od (?:wynik|sprzedaz|efekt|obrot|zaangazowani)\\w*|zalezn\\w* od (?:wynik|sprzedaz|efekt|obrot)\\w*"
                    + "|wynagrodzeni\\w* prowizyjn\\w*|wylacznie (?:z )?prowizj\\w*|tylko prowizj\\w*|system\\w* prowizyjn\\w*"
                    + "|nielimitowan\\w* (?:zarobk|wynagrodz|prowizj)\\w*|commission[- ]only|uncapped commission"
                    + "|unlimited earnings|performance[- ]based pay");
    private static final Pattern BASE_PAY = Evidence.p(
            "podstaw\\w*|stal\\w* (?:pensj|wynagrodz|czesc)\\w*|base salary|fixed salary|gwarantowan\\w*|\\+\\s*premi\\w*"
                    + "|\\+\\s*prowizj\\w*");
    private static final Pattern PHOTO = Evidence.p("zdjeci\\w*|fotografi\\w*|\\bphoto\\w*|\\bpicture\\b|wizerun\\w*");
    private static final Pattern PHOTO_CONTEXT = Evidence.p(
            "\\bcv\\b|zyciorys|aplik|zgloszen|resume|prosimy|przeslij|przesyla|dolacz|zalacz|wymagan|application|attach"
                    + "|include");
    private static final Pattern PHOTO_NOT = Evidence.p(
            "dowod|paszport|dokument|obrobk|edycj|retusz|sesj|aparat|grafik|instagram|social");
    private static final Pattern BIRTH_DATE = Evidence.p("dat\\w* urodzenia|date of birth|rok\\w* urodzenia");
    private static final Pattern PRIVATE_DATA = Evidence.p(
            "stan\\w* cywiln\\w*|marital status|stan\\w* rodzinn\\w*|sytuacj\\w* rodzinn\\w*|liczb\\w* dzieci"
                    + "|posiadan\\w* dzieci|czy (?:ma|masz|posiada\\w*) dzieci|plan\\w* (?:rodzinn|macierzyn|prokreac)\\w*"
                    + "|\\bw ciazy\\b|\\bciaz[ay]\\b|\\bciaze\\b|wyznani\\w*|religi\\w*|orientacj\\w* seksualn\\w*"
                    + "|przynaleznosc\\w* (?:partyjn|zwiazkow|polityczn)\\w*|narodowosc\\w*|wag\\w* ciala");
    /** Linie klauzul RODO / informacji „nie podawaj…” — tam wzmianki o danych nie są prośbą. */
    private static final Pattern CLAUSE = Evidence.p(
            "art\\.?\\s*22|kodeks|rodo|administrator\\w*|przetwarzani\\w*|nie (?:podawaj|umieszczaj|przesylaj|zamieszczaj)"
                    + "|prosimy nie|art\\.?\\s*9|szczegoln\\w* kategori");
    private static final Pattern AGE_LIMIT = Evidence.p(
            "w wieku (?:od |do )?\\d{2}\\b|\\bwiek\\w*:?\\s+(?:od |do |ponizej |powyzej |max\\.? |maks\\.? )?\\d{2}\\b"
                    + "|do \\d{2}\\.? (?:roku|r\\.) zycia|(?:ponizej|do) [3-6]\\d (?:roku zycia|lat\\b(?! doswiadcz)(?! stazu))"
                    + "|age (?:limit|under|between|of) \\d{2}|(?:osob\\w*|kandydat\\w*) w wieku");
    private static final Pattern STUDENT = Evidence.p("student|uczni|uczen|26");
    private static final Pattern NO_CONTRACT = Evidence.p(
            "\\bbez umowy\\b|na czarno|umow\\w* (?:podpiszemy |podpisujemy |zawieramy |zawrzemy )?(?:po|od) (?:okresie probnym"
                    + "|\\d+\\.? ?(?:miesiac|tygodn|dni)\\w*|pierwsz\\w* miesiac\\w*|drugi\\w* miesiac\\w*|okresie wdrozeni\\w*)"
                    + "|praca bez umowy|bez formalnosci");

    private RedFlagDetector() {
    }

    public static List<RedFlag> detect(JobOffer offer) {
        String description = Text.nullToEmpty(offer.getDescription());
        String title = Text.nullToEmpty(offer.getTitle());
        String text = description.contains(title) || title.isBlank() ? description : title + "\n\n" + description;
        Map<String, RedFlag> flags = new LinkedHashMap<>();

        // --- brak widełek / szerokie widełki ---
        Optional<SalaryRange> parsed = OfferTextParser.findSalary(description);
        boolean hasSalary = offer.getSalaryMin() != null || offer.getSalaryMax() != null || parsed.isPresent();
        if (!hasSalary) {
            String quote = Evidence.find(text, VAGUE_PAY).map(Evidence.Hit::quote).orElse(null);
            add(flags, "NO_SALARY", Severity.WARNING, "Brak widełek wynagrodzenia",
                    (quote != null ? "Ogólnik o „atrakcyjnym wynagrodzeniu” bez kwot nic nie mówi. " : "")
                            + "Bez kwot trudno ocenić, czy oferta jest warta Twojego czasu, i łatwiej o zaniżoną propozycję. "
                            + "Od 24 grudnia 2025 r. pracodawca musi przekazać kandydatowi do pracy na umowę o pracę informację "
                            + "o wynagrodzeniu początkowym lub jego przedziale (np. w ogłoszeniu albo przed rozmową) — "
                            + "zapytaj o widełki, zanim poświęcisz czas na rekrutację.",
                    quote);
        } else {
            Integer min = offer.getSalaryMin() != null ? offer.getSalaryMin() : parsed.map(SalaryRange::min).orElse(null);
            Integer max = offer.getSalaryMax() != null ? offer.getSalaryMax() : parsed.map(SalaryRange::max).orElse(null);
            if (min != null && max != null && min > 0 && max >= min * WIDE_RANGE_RATIO) {
                double ratio = Math.round((double) max / min * 10) / 10.0;
                String quote = parsed.map(SalaryRange::matchedText).filter(q -> Evidence.isVerbatim(description, q))
                        .map(q -> Evidence.quote(description, description.indexOf(q), description.indexOf(q) + q.length()))
                        .orElse(null);
                add(flags, "WIDE_SALARY_RANGE", Severity.INFO, "Bardzo szerokie widełki",
                        "Górna granica jest " + String.format(Locale.ROOT, "%.1f", ratio).replace('.', ',')
                                + "× wyższa niż dolna. Tak szerokie widełki często oznaczają, że firma nie wie, kogo szuka, "
                                + "albo górna kwota jest dla nielicznych. Zapytaj, od czego zależy stawka i ile zarabiają "
                                + "osoby na tym stanowisku.",
                        quote);
            }
        }

        // --- umowa o dzieło przy stałej pracy ---
        Optional<Evidence.Hit> uod = Evidence.find(text, UOD).filter(h -> !UOD_NEGATION.matcher(h.foldedLine()).find());
        boolean uodContract = offer.getContractTypes() != null && offer.getContractTypes().contains(ContractType.UOD);
        if (uod.isPresent() || uodContract) {
            boolean continuous = CONTINUOUS_WORK.matcher(Text.fold(text)).find();
            add(flags, "UOD_FOR_JOB", continuous ? Severity.HIGH : Severity.WARNING,
                    continuous ? "Umowa o dzieło przy stałej pracy" : "Umowa o dzieło",
                    "Umowa o dzieło dotyczy konkretnego rezultatu, a nie stałego wykonywania obowiązków. Jeśli pracujesz "
                            + "pod kierownictwem, w ustalonym miejscu i czasie, jest to stosunek pracy bez względu na nazwę umowy "
                            + "(art. 22 § 1¹ i § 1² KP). „Dzieło” to brak składek emerytalnych, ubezpieczenia zdrowotnego, "
                            + "płatnego urlopu i gwarancji płacy minimalnej.",
                    uod.map(Evidence.Hit::quote).orElse(null));
        }

        // --- bezpłatny okres próbny / dni próbne / staż ---
        Optional<Evidence.Hit> unpaidTrial = Evidence.find(text, UNPAID_TRIAL);
        if (unpaidTrial.isPresent()) {
            add(flags, "UNPAID_TRIAL", Severity.HIGH, "Nieodpłatny okres próbny lub dni próbne",
                    "Okres próbny to umowa o pracę na okres próbny (art. 25 KP, maks. 3 miesiące) — za pracę w tym czasie "
                            + "należy się wynagrodzenie. Praca „na próbę” bez zapłaty to darmowa praca; umiejętności można "
                            + "sprawdzić zadaniem rekrutacyjnym, a nie dniami pracy bez umowy.",
                    unpaidTrial.get().quote());
        } else {
            Evidence.find(text, TRIAL_DAYS).ifPresent(h -> add(flags, "TRIAL_DAYS", Severity.WARNING, "Dni próbne",
                    "Zapytaj, czy dni próbne są płatne i na podstawie jakiej umowy. Wykonywanie pracy bez umowy "
                            + "i wynagrodzenia to sygnał ostrzegawczy.",
                    h.quote()));
        }
        Evidence.find(text, UNPAID_INTERNSHIP).ifPresent(h -> add(flags, "UNPAID_INTERNSHIP", Severity.INFO,
                "Bezpłatny staż lub praktyki",
                "Bezpłatne praktyki bywają legalne (np. studenckie), ale jeśli w praktyce wykonujesz zwykłą pracę na rzecz "
                        + "firmy, należy się za nią wynagrodzenie. Dopytaj o zakres zadań, czas trwania i szansę na zatrudnienie.",
                h.quote()));

        // --- praca bez umowy ---
        Evidence.find(text, NO_CONTRACT).ifPresent(h -> add(flags, "NO_CONTRACT", Severity.HIGH,
                "Praca bez umowy lub umowa „później”",
                "Pracodawca musi potwierdzić na piśmie rodzaj umowy i jej warunki najpóźniej przed dopuszczeniem Cię do "
                        + "pracy (art. 29 § 2 KP). Praca „na czarno” to brak ubezpieczenia (także wypadkowego) i ochrony prawnej.",
                h.quote()));

        // --- dyspozycyjność, nadgodziny ---
        Optional<Evidence.Hit> availability = Evidence.find(text, AVAILABILITY);
        availability.ifPresent(h -> add(flags, "AVAILABILITY_24_7", Severity.HIGH, "Dyspozycyjność 24/7",
                "Pracownikowi przysługuje co najmniej 11 godzin nieprzerwanego odpoczynku na dobę (art. 132 KP) i 35 godzin "
                        + "w tygodniu (art. 133 KP). Wymóg stałej dyspozycyjności zwykle oznacza pracę ponad normy — dopytaj "
                        + "o grafik, dyżury (art. 151⁵ KP) i wynagrodzenie za gotowość do pracy.",
                h.quote()));
        for (Evidence.Hit h : Evidence.findAll(text, OVERTIME, 5)) {
            if (OVERTIME_OK.matcher(h.foldedLine()).find()) {
                continue;
            }
            boolean paid = OVERTIME_PAID.matcher(h.foldedLine()).find();
            add(flags, "OVERTIME", paid ? Severity.INFO : Severity.WARNING, "Nadgodziny lub praca w weekendy",
                    "Nadgodziny są dopuszczalne tylko w szczególnych sytuacjach i z limitem (domyślnie 150 godzin rocznie, "
                            + "art. 151 § 3 KP), a za każdą należy się dodatek 50% lub 100% albo czas wolny (art. 151¹ KP). "
                            + "Zapytaj, jak często się zdarzają i jak są rozliczane.",
                    h.quote());
            break;
        }

        // --- stres, „młody zespół”, „rodzinna atmosfera” ---
        Evidence.find(text, STRESS).ifPresent(h -> add(flags, "STRESS", Severity.WARNING,
                "„Odporność na stres” / „praca pod presją”",
                "Taki wymóg często sygnalizuje chroniczne przeciążenie, napięte terminy albo złą organizację pracy. Zapytaj "
                        + "o typowy dzień, liczbę zadań na osobę i rotację w zespole.",
                h.quote()));
        Evidence.find(text, YOUNG_TEAM).ifPresent(h -> add(flags, "YOUNG_TEAM", Severity.WARNING,
                "„Młody, dynamiczny zespół”",
                "Może sugerować preferowanie kandydatów ze względu na wiek — to niedozwolone kryterium (art. 18³ᵃ § 1 KP) — "
                        + "albo dużą rotację i brak doświadczonych osób. Zapytaj o staż osób w zespole i rotację.",
                h.quote()));
        Evidence.find(text, FAMILY).ifPresent(h -> add(flags, "FAMILY_ATMOSPHERE", Severity.INFO, "„Rodzinna atmosfera”",
                "Częsty eufemizm: rozmyte granice między pracą a życiem prywatnym, oczekiwanie poświęceń „dla zespołu”, "
                        + "nieformalne zasady zamiast procedur. Zapytaj o konkretne praktyki: nadgodziny, urlopy, rozwiązywanie "
                        + "konfliktów.",
                h.quote()));

        // --- wynagrodzenie zależne od wyników ---
        Evidence.find(text, RESULTS_PAY).ifPresent(h -> {
            boolean base = offer.getSalaryMin() != null || BASE_PAY.matcher(Text.fold(text)).find();
            if (base) {
                add(flags, "RESULTS_PAY", Severity.INFO, "Część wynagrodzenia zależna od wyników",
                        "Dopytaj o proporcję podstawy do części zmiennej i o to, jak dokładnie liczone są wyniki.", h.quote());
            } else {
                add(flags, "RESULTS_ONLY_PAY", hasSalary ? Severity.WARNING : Severity.HIGH,
                        "Wynagrodzenie „uzależnione od wyników” bez podstawy",
                        "Bez gwarantowanej podstawy ryzyko przechodzi na Ciebie. Na umowie o pracę pensja za pełny etat nie "
                                + "może być niższa niż płaca minimalna, a na umowie zlecenia obowiązuje minimalna stawka "
                                + "godzinowa (ustawa o minimalnym wynagrodzeniu za pracę). Poproś o kwotę podstawy i zasady "
                                + "premii na piśmie.",
                        h.quote());
            }
        });

        // --- dane osobowe ponad art. 22¹ KP ---
        for (Evidence.Hit h : Evidence.findAll(text, PHOTO, 10)) {
            String line = h.foldedLine();
            if (PHOTO_CONTEXT.matcher(line).find() && !PHOTO_NOT.matcher(line).find() && !CLAUSE.matcher(line).find()) {
                add(flags, "PHOTO_REQUESTED", Severity.WARNING, "Prośba o zdjęcie w CV",
                        "Wizerunek nie należy do danych, których pracodawca może żądać od kandydata (art. 22¹ § 1 KP). Może "
                                + "go przetwarzać tylko za Twoją zgodą (art. 22¹ᵃ KP) — zdjęcie nie jest potrzebne do oceny "
                                + "kwalifikacji i możesz go nie dołączać.",
                        h.quote());
                break;
            }
        }
        for (Evidence.Hit h : Evidence.findAll(text, BIRTH_DATE, 10)) {
            if (!CLAUSE.matcher(h.foldedLine()).find()) {
                add(flags, "BIRTH_DATE_REQUESTED", Severity.INFO, "Prośba o datę urodzenia",
                        "Datę urodzenia pracodawca może zebrać (art. 22¹ § 1 pkt 2 KP), ale nie jest ona potrzebna do oceny "
                                + "kompetencji, a wiek nie może być kryterium wyboru (art. 18³ᵃ KP). Zwróć uwagę, czy ogłoszenie "
                                + "nie sugeruje preferencji wiekowych.",
                        h.quote());
                break;
            }
        }
        for (Evidence.Hit h : Evidence.findAll(text, PRIVATE_DATA, 10)) {
            if (!CLAUSE.matcher(h.foldedLine()).find()) {
                add(flags, "PRIVATE_DATA_REQUESTED", Severity.HIGH, "Prośba o stan cywilny lub sytuację rodzinną",
                        "Stan cywilny, plany rodzinne, liczba dzieci czy wyznanie nie należą do danych, których można żądać "
                                + "od kandydata (art. 22¹ § 1 KP). Pytania o nie mogą prowadzić do dyskryminacji (art. 18³ᵃ KP) — "
                                + "nie musisz ich podawać.",
                        h.quote());
                break;
            }
        }
        Evidence.find(text, AGE_LIMIT).ifPresent(h -> {
            boolean student = h.foldedLine().contains("26") && STUDENT.matcher(h.foldedLine().replace("26", "")).find();
            if (student) {
                add(flags, "AGE_LIMIT", Severity.INFO, "Ograniczenie do osób przed 26. rokiem życia",
                        "Zwykle wynika z korzystnych dla pracodawcy zasad oskładkowania umów zlecenia ze studentami — zapytaj "
                                + "o formę umowy i o to, co stanie się po ukończeniu 26 lat.",
                        h.quote());
            } else {
                add(flags, "AGE_LIMIT", Severity.HIGH, "Limit wieku w ogłoszeniu",
                        "Wiek nie może być kryterium zatrudnienia (zakaz dyskryminacji, art. 18³ᵃ § 1 KP), chyba że wynika "
                                + "to z przepisów lub charakteru pracy.",
                        h.quote());
            }
        });

        // --- wygórowane wymagania dla juniora ---
        Seniority seniority = offer.getSeniority() != null ? offer.getSeniority()
                : EmploymentTermsDetector.seniority(title, null, null).map(EmploymentTermsDetector.Detection::value).orElse(null);
        if (seniority == Seniority.JUNIOR || seniority == Seniority.INTERN) {
            juniorOverreach(offer, description, flags);
        }

        List<RedFlag> out = new ArrayList<>(flags.values());
        out.sort(Comparator.comparingInt((RedFlag f) -> -f.severity().ordinal()));
        return out;
    }

    private static void juniorOverreach(JobOffer offer, String description, Map<String, RedFlag> flags) {
        List<Requirement> reqs = offer.getRequirements() == null ? List.of() : offer.getRequirements();
        Requirement maxYearsReq = null;
        int maxYears = 0;
        int must = 0;
        for (Requirement r : reqs) {
            if (!r.mustHave()) {
                continue;
            }
            must++;
            Integer y = r.minYears() != null ? r.minYears() : YearsParser.requiredYears(r.text());
            if (y != null && y > maxYears) {
                maxYears = y;
                maxYearsReq = r;
            }
        }
        if (maxYears >= JUNIOR_MAX_YEARS) {
            String quote = maxYearsReq != null && Evidence.isVerbatim(description, maxYearsReq.text()) ? maxYearsReq.text() : null;
            add(flags, "JUNIOR_OVERREQUIREMENTS", Severity.WARNING, "Wygórowane wymagania dla juniora",
                    "Stanowisko juniorskie, a wymagane " + maxYears + " " + years(maxYears) + " doświadczenia. To często oferta "
                            + "dla mida za stawkę juniora — zapytaj, które wymagania są naprawdę konieczne.",
                    quote);
        } else if (must >= JUNIOR_MAX_MUST) {
            add(flags, "JUNIOR_OVERREQUIREMENTS", Severity.WARNING, "Wygórowane wymagania dla juniora",
                    "Stanowisko juniorskie, a lista wymagań obowiązkowych ma " + must + " pozycji. Zapytaj, które z nich są "
                            + "naprawdę konieczne na start, a których można się nauczyć w pracy.",
                    null);
        }
    }

    private static String years(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (n == 1) {
            return "rok";
        }
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "lata" : "lat";
    }

    private static void add(Map<String, RedFlag> flags, String code, Severity severity, String title, String why,
                            String quote) {
        flags.putIfAbsent(code, new RedFlag(code, severity, title, why, quote));
    }
}

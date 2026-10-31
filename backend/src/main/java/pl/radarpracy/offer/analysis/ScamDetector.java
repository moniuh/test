package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.analysis.ScamAssessment.RiskLevel;
import pl.radarpracy.offer.analysis.ScamAssessment.Signal;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Wykrywanie ogłoszeń-oszustw (pomysł #19). Każdy sygnał ma jawną liczbę punktów; wynik to suma
 * (maks. 100), a poziom ryzyka: niskie &lt; {@value #MEDIUM}, średnie &lt; {@value #HIGH}, wysokie ≥ {@value #HIGH}.
 *
 * <table>
 *   <caption>Punkty sygnałów</caption>
 *   <tr><td>opłata za szkolenie / pakiet startowy / rejestrację</td><td>40</td></tr>
 *   <tr><td>przyjmowanie i przesyłanie paczek</td><td>35</td></tr>
 *   <tr><td>przekazywanie pieniędzy, przelewy, zaliczka do przelania</td><td>35 (sama „zaliczka”: 20)</td></tr>
 *   <tr><td>skan dowodu, PESEL, numer konta na starcie</td><td>30</td></tr>
 *   <tr><td>kontakt tylko przez WhatsApp/Telegram (sama wzmianka: 10)</td><td>25</td></tr>
 *   <tr><td>nierealna stawka bez doświadczenia</td><td>25</td></tr>
 *   <tr><td>wypłata w kryptowalutach</td><td>20</td></tr>
 *   <tr><td>„łatwe pieniądze”, „zarabiaj X zł dziennie”</td><td>20</td></tr>
 *   <tr><td>marketing sieciowy / „zbuduj swoją sieć”</td><td>15</td></tr>
 *   <tr><td>prywatna skrzynka e-mail, brak nazwy firmy</td><td>10 każde</td></tr>
 *   <tr><td>presja („!!!”, „ostatnie miejsca”), krzykliwy tytuł</td><td>5</td></tr>
 * </table>
 */
public final class ScamDetector {

    static final int MEDIUM = 25;
    static final int HIGH = 55;
    /** Stawka dzienna (PLN), od której praca „bez doświadczenia” jest nierealna. */
    static final int UNREALISTIC_DAILY_PLN = 400;

    private static final Pattern FEE = Evidence.p(
            "\\b(?:oplat\\w*|wplat\\w*|zaplac\\w*|zaplat\\w*|kaucj\\w*|wpisow\\w*|depozyt\\w*|fee|pay (?:for|a fee))");
    private static final Pattern FEE_CONTEXT = Evidence.p(
            "szkoleni|pakiet|start|rejestracj|materia|kaucj|wpisow|aktywacj|zestaw|\\bkit\\b|training|registration"
                    + "|jednorazow|deposit|depozyt|onboarding|licencj|dostep");
    private static final Pattern FEE_NOT = Evidence.p(
            "pokrywamy|dofinans|zwrot|refund|finansujemy|nie pobieramy|bez oplat|zadnych oplat|bezplatn|darmow|no fee"
                    + "|free of charge|za darmo|nie musisz|nie ponosisz");
    private static final Pattern PARCEL = Evidence.p(
            "\\bpacz(?:ka|ki|ek|kami|kach|ke|kom)\\b|\\bparcels?\\b|\\bpackages?\\b|przepakow\\w*|reship\\w*");
    private static final Pattern PARCEL_CONTEXT = Evidence.p(
            "w domu|z domu|pod wskazany|wskazany adres|na (?:swoj|twoj|wlasn)\\w* adres|\\bdalej\\b|forward|reship"
                    + "|from home|at home|your (?:home|address)|przepakow|nadaw\\w* (?:je|ponownie)");
    private static final Pattern MONEY = Evidence.p(
            "(?:przelew\\w*|przelac\\w*|przelewa\\w*|przekaz\\w*|przesyla\\w*|przesl\\w*|wysyla\\w*|wyplac\\w*|odbier\\w*)"
                    + "\\s+(?:\\w+\\s+){0,3}?(?:pieniedz\\w*|pieniadz\\w*|srodk\\w*|gotowk\\w*|wplat\\w*|kwot\\w*|zaliczk\\w*"
                    + "|przelew\\w* dalej)|western union|moneygram|\\bkod\\w* blik\\w*|\\bblik\\w*"
                    + "|transfer(?:ring)? (?:the )?(?:money|funds|payments)|money transfer agent|payment processing agent"
                    + "|\\bmoney mule");
    private static final Pattern ADVANCE = Evidence.p("zaliczk\\w*");
    private static final Pattern ID_DOCS = Evidence.p(
            "skan\\w* (?:\\w+ )?(?:dowodu|paszportu|dokument\\w*)|zdjeci\\w* (?:\\w+ )?(?:dowodu|paszportu|dokument\\w*)"
                    + "|kopi\\w* (?:\\w+ )?(?:dowodu|paszportu)|selfie z dowodem|dowod\\w* osobist\\w*"
                    + "|numer\\w* (?:dowodu|paszportu)|\\bpesel\\w*|numer\\w* (?:\\w+ )?(?:konta|rachunku)|dane (?:do )?(?:karty|logowania)"
                    + "|numer\\w* karty|login\\w* (?:do )?(?:banku|bankowosci)|hasl\\w* do (?:banku|konta)"
                    + "|(?:id|passport) (?:scan|copy)|copy of (?:your )?(?:id|passport)|bank account (?:number|details)"
                    + "|social security number");
    private static final Pattern ID_DOCS_NOT = Evidence.p(
            "przy podpisaniu|w dniu zatrudnienia|po zatrudnieniu|przy zawarciu umowy|na etapie umowy|upon hiring|rodo"
                    + "|administrator\\w*");
    private static final Pattern MESSENGER = Evidence.p(
            "whats ?app|telegram\\w*|\\bviber\\b|messenger\\w*|\\bwechat\\b");
    private static final Pattern ONLY = Evidence.p(
            "wylacznie|tylko|jedynie|\\bonly\\b|exclusively|nie dzwon|bez rozmow");
    private static final Pattern CRYPTO = Evidence.p(
            "kryptowalut\\w*|\\bkrypto\\w*|bitcoin\\w*|\\bbtc\\b|\\busdt\\b|tether|\\bcrypto\\w*|binance");
    private static final Pattern PAY_CONTEXT = Evidence.p(
            "wyplat|wynagrodz|platn|zaplat|\\bpaid\\b|\\bpay|salary|przelew|zarob|rozlicz|premi");
    private static final Pattern NO_EXPERIENCE = Evidence.p(
            "bez doswiadczenia|nie wymagamy doswiadczenia|doswiadczenie nie jest wymagane|no (?:prior )?experience"
                    + "|zero experience|kazdy moze|dla kazdego");
    private static final Pattern EASY_MONEY = Evidence.p(
            "latw\\w* (?:pieniadz|pieniedz|zarob)\\w*|szybk\\w* (?:pieniadz|pieniedz|zarob)\\w*|easy money"
                    + "|pasywn\\w* dochod\\w*|nielimitowan\\w* zarob\\w*|unlimited (?:income|earnings)"
                    + "|zarob\\w* (?:nawet |do |ponad )?\\d[\\d .]*\\s*(?:zl|pln)\\s*(?:dziennie|tygodniowo|w tydzien|na dzien)");
    private static final Pattern MLM = Evidence.p(
            "marketing\\w* sieciow\\w*|\\bmlm\\b|zbuduj (?:swoj|wlasn)\\w* (?:zespol|siec)\\w*|system\\w* polecen\\w*"
                    + "|network marketing|struktur\\w* sprzedaz\\w*");
    private static final Pattern PRIVATE_EMAIL = Evidence.p(
            "[\\w.+-]+@(?:gmail|wp|o2|interia|onet|op|yahoo|outlook|hotmail|tlen|poczta|protonmail|proton|icloud)\\.(?:com|pl|me|eu)");
    private static final Pattern URGENCY = Evidence.p(
            "!{3,}|ostatni\\w* miejsc\\w*|tylko dzis|liczba miejsc (?:jest )?ogranicz\\w*|limited (?:spots|places)"
                    + "|zglos sie (?:juz )?dzis");

    private ScamDetector() {
    }

    public static ScamAssessment assess(JobOffer offer) {
        String description = Text.nullToEmpty(offer.getDescription());
        String title = Text.nullToEmpty(offer.getTitle());
        String text = description.contains(title) || title.isBlank() ? description : title + "\n\n" + description;
        Map<String, Signal> signals = new LinkedHashMap<>();

        firstInContext(text, FEE, FEE_CONTEXT, FEE_NOT).ifPresent(h -> add(signals, "UPFRONT_FEE",
                "Opłata za szkolenie, pakiet startowy lub rejestrację", 40,
                "Legalny pracodawca nie pobiera opłat za zatrudnienie, szkolenie stanowiskowe ani „pakiet startowy”. "
                        + "To najczęstszy schemat oszustw na ogłoszenia o pracę.", h.quote()));
        firstInContext(text, PARCEL, PARCEL_CONTEXT, null).ifPresent(h -> add(signals, "PARCEL_MULE",
                "Przyjmowanie i przesyłanie paczek", 35,
                "Odbieranie paczek w domu i odsyłanie ich „dalej” to typowy schemat wyłudzeń (towar kupiony na cudze "
                        + "dane) — osoba „zatrudniona” może odpowiadać m.in. za paserstwo.", h.quote()));
        Optional<Evidence.Hit> money = Evidence.find(text, MONEY);
        if (money.isPresent()) {
            add(signals, "MONEY_TRANSFER", "Przelewy, zaliczki lub przekazywanie pieniędzy", 35,
                    "Przyjmowanie pieniędzy na swoje konto i przekazywanie ich dalej (albo „zaliczka” od nieznanej firmy) "
                            + "to werbowanie tzw. słupów — grozi odpowiedzialnością karną za pranie pieniędzy (art. 299 KK).",
                    money.get().quote());
        } else {
            Evidence.find(text, ADVANCE).ifPresent(h -> add(signals, "ADVANCE_PAYMENT", "Zaliczka od pracodawcy", 20,
                    "„Zaliczka” na start od nieznanej firmy bywa pretekstem do zdobycia numeru konta lub do przelewów, "
                            + "które trzeba potem „oddać” — sprawdź, na jakiej podstawie miałaby być wypłacona.", h.quote()));
        }
        firstInContext(text, ID_DOCS, null, ID_DOCS_NOT).ifPresent(h -> add(signals, "ID_DOCUMENTS",
                "Prośba o dokumenty, PESEL lub numer konta na starcie", 30,
                "Skan dowodu, PESEL czy numer konta na etapie ogłoszenia służą często do kradzieży tożsamości (np. kredytu "
                        + "na Twoje dane). Pracodawca potrzebuje ich dopiero przy zawieraniu umowy.", h.quote()));
        Optional<Evidence.Hit> messenger = Evidence.find(text, MESSENGER);
        messenger.ifPresent(h -> {
            if (ONLY.matcher(h.foldedLine()).find()) {
                add(signals, "MESSENGER_ONLY", "Kontakt tylko przez komunikator", 25,
                        "Rekruterzy prawdziwych firm umawiają rozmowę i piszą z firmowego adresu. Kontakt wyłącznie przez "
                                + "WhatsApp/Telegram utrudnia ustalenie, kto stoi za ogłoszeniem.", h.quote());
            } else {
                add(signals, "MESSENGER", "Kontakt przez komunikator", 10,
                        "Sam komunikator nie przesądza o oszustwie, ale sprawdź, czy da się porozmawiać z firmą "
                                + "przez oficjalne kanały.", h.quote());
            }
        });
        firstInContext(text, CRYPTO, PAY_CONTEXT, null).ifPresent(h -> add(signals, "CRYPTO",
                "Wypłata w kryptowalutach", 20,
                "Wynagrodzenie w kryptowalutach utrudnia dochodzenie należności i bywa elementem prania pieniędzy.",
                h.quote()));
        unrealisticPay(offer, text).ifPresent(s -> signals.put(s.code(), s));
        Evidence.find(text, EASY_MONEY).ifPresent(h -> add(signals, "EASY_MONEY", "Obietnica łatwych pieniędzy", 20,
                "Obietnice szybkiego, łatwego zarobku bez konkretów o pracy to typowy wabik w ogłoszeniach-oszustwach.",
                h.quote()));
        Evidence.find(text, MLM).ifPresent(h -> add(signals, "MLM", "Marketing sieciowy / budowanie „struktury”", 15,
                "Zarobek zależny od werbowania kolejnych osób to cecha marketingu wielopoziomowego, a w skrajnym "
                        + "przypadku piramidy finansowej.", h.quote()));
        Evidence.find(text, PRIVATE_EMAIL).ifPresent(h -> add(signals, "PRIVATE_EMAIL", "Kontakt przez prywatną skrzynkę",
                10, "Firmy rekrutują z adresów we własnej domenie. Prywatna skrzynka (np. gmail.com) utrudnia weryfikację.",
                h.quote()));
        if (Text.isBlank(offer.getCompany())) {
            add(signals, "NO_COMPANY", "Brak nazwy pracodawcy", 10,
                    "Ogłoszenia bez nazwy firmy nie da się sprawdzić w rejestrach (KRS/CEIDG).", null);
        }
        Optional<Evidence.Hit> urgency = Evidence.find(text, URGENCY);
        if (urgency.isPresent()) {
            add(signals, "PRESSURE", "Presja czasu i krzykliwy ton", 5,
                    "„!!!”, „ostatnie miejsca” czy „tylko dziś” mają skłonić do działania bez sprawdzenia oferty.",
                    urgency.get().quote());
        } else if (shouting(title)) {
            add(signals, "PRESSURE", "Krzykliwy tytuł (wielkie litery)", 5,
                    "Tytuł pisany wielkimi literami ma przyciągnąć uwagę zamiast opisywać stanowisko.", title.strip());
        }

        List<Signal> list = new ArrayList<>(signals.values());
        int score = Math.min(100, list.stream().mapToInt(Signal::points).sum());
        RiskLevel level = score >= HIGH ? RiskLevel.HIGH : score >= MEDIUM ? RiskLevel.MEDIUM : RiskLevel.LOW;
        return new ScamAssessment(level, level.label(), score, MEDIUM, HIGH, list, recommendations(signals.keySet(), level),
                "Reguły słów kluczowych działające offline — suma punktów sygnałów (maks. 100). Ocena pomocnicza: "
                        + "decyzja należy do Ciebie.");
    }

    private static Optional<Signal> unrealisticPay(JobOffer offer, String text) {
        Optional<Evidence.Hit> noExp = Evidence.find(text, NO_EXPERIENCE);
        if (noExp.isEmpty()) {
            return Optional.empty();
        }
        Integer amount = offer.getSalaryMax() != null ? offer.getSalaryMax() : offer.getSalaryMin();
        SalaryPeriod period = offer.getSalaryPeriod();
        String currency = offer.getSalaryCurrency();
        String quote = null;
        if (amount == null) {
            Optional<SalaryRange> r = OfferTextParser.findSalary(text);
            if (r.isPresent()) {
                amount = r.get().max() != null ? r.get().max() : r.get().min();
                period = r.get().period();
                currency = r.get().currency();
                quote = r.get().matchedText();
            }
        }
        if (amount == null || currency != null && !"PLN".equalsIgnoreCase(currency)) {
            return Optional.empty();
        }
        double daily = switch (period == null ? SalaryPeriod.MONTH : period) {
            case HOUR -> amount * 8.0;
            case DAY -> amount;
            case MONTH -> amount / 21.0;
            case YEAR -> amount / 252.0;
        };
        if (daily < UNREALISTIC_DAILY_PLN) {
            return Optional.empty();
        }
        String q = noExp.get().quote();
        if (quote != null && !q.contains(quote) && Evidence.isVerbatim(text, quote)) {
            int idx = text.indexOf(quote);
            q = Evidence.quote(text, idx, idx + quote.length());
        }
        return Optional.of(new Signal("UNREALISTIC_PAY", "Nierealna stawka bez doświadczenia", 25,
                "Ok. " + Math.round(daily) + " zł dziennie bez wymaganego doświadczenia to stawka wielokrotnie wyższa niż "
                        + "na podobnych stanowiskach — tak wabi się do „pracy”, która jest udziałem w przestępstwie.",
                q));
    }

    private static Optional<Evidence.Hit> firstInContext(String text, Pattern p, Pattern mustHave, Pattern mustNot) {
        for (Evidence.Hit h : Evidence.findAll(text, p, 20)) {
            String line = h.foldedLine();
            if ((mustHave == null || mustHave.matcher(line).find()) && (mustNot == null || !mustNot.matcher(line).find())) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    private static boolean shouting(String title) {
        long letters = title.chars().filter(Character::isLetter).count();
        long upper = title.chars().filter(Character::isUpperCase).count();
        return letters >= 12 && upper >= letters * 0.6;
    }

    private static List<String> recommendations(Set<String> codes, RiskLevel level) {
        Set<String> out = new LinkedHashSet<>();
        if (codes.contains("UPFRONT_FEE")) {
            out.add("Nie płać za szkolenie, „pakiet startowy”, rejestrację ani kaucję — koszty rekrutacji ponosi pracodawca.");
        }
        if (codes.contains("ID_DOCUMENTS")) {
            out.add("Nie wysyłaj skanu dowodu, numeru PESEL, numeru konta ani danych karty przed podpisaniem umowy "
                    + "i sprawdzeniem firmy.");
        }
        if (codes.contains("PARCEL_MULE") || codes.contains("MONEY_TRANSFER") || codes.contains("ADVANCE_PAYMENT")) {
            out.add("Nie przyjmuj paczek ani przelewów do „przekazania dalej” — możesz odpowiadać karnie (paserstwo, "
                    + "pranie pieniędzy).");
        }
        if (codes.contains("MESSENGER_ONLY") || codes.contains("MESSENGER")) {
            out.add("Poproś o rozmowę wideo i kontakt przez firmowy e-mail; sprawdź rekrutera na LinkedIn i na stronie firmy.");
        }
        if (codes.contains("CRYPTO")) {
            out.add("Wymagaj wynagrodzenia w złotych, na konto, na podstawie pisemnej umowy.");
        }
        if (codes.contains("PRIVATE_EMAIL") || codes.contains("NO_COMPANY")) {
            out.add("Ustal pełną nazwę firmy, NIP i adres — bez nich ogłoszenia nie da się zweryfikować.");
        }
        if (level != RiskLevel.LOW) {
            out.add("Sprawdź firmę w KRS (wyszukiwarka-krs.ms.gov.pl) lub CEIDG (biznes.gov.pl): nazwę, NIP, adres "
                    + "i datę rejestracji.");
        }
        if (level == RiskLevel.HIGH) {
            out.add("Jeśli przekazał(a)ś już dane lub pieniądze: zastrzeż PESEL (aplikacja mObywatel lub gov.pl) i dowód "
                    + "osobisty (np. w banku), zgłoś sprawę na policję i do CERT Polska (incydent.cert.pl).");
        }
        if (level == RiskLevel.LOW) {
            out.add("Nie wykryto typowych sygnałów oszustwa. Mimo to przed wysłaniem danych osobowych sprawdź, czy firma "
                    + "istnieje (KRS/CEIDG).");
        }
        return List.copyOf(out);
    }

    private static void add(Map<String, Signal> signals, String code, String title, int points, String why, String quote) {
        signals.putIfAbsent(code, new Signal(code, title, points, why, quote));
    }
}

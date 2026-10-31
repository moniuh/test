package pl.radarpracy.profile.cv;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.profile.cv.CvStructure.Line;
import pl.radarpracy.profile.cv.CvStructure.SectionType;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.skills.SkillHit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audyt CV pod ATS (pomysł #4): deterministyczny wynik 0–100 z listą kontroli i poprawek.
 *
 * <p>Każda kontrola ma jawną wagę (suma wag = 100). Status „ok” daje pełną wagę, „warning” — połowę
 * ({@value #WARNING_CREDIT}), „error” — zero. Wynik to suma punktów zaokrąglona do liczby całkowitej.
 * Progi są stałymi poniżej, a przykłady w kontrolach to dosłowne fragmenty CV.</p>
 */
@Service
public class CvAuditService {

    // --- wagi kontroli (suma = 100) ---
    static final int W_EMAIL = 8;
    static final int W_PHONE = 5;
    static final int W_EXPERIENCE = 12;
    static final int W_SKILLS_SECTION = 8;
    static final int W_EDUCATION = 6;
    static final int W_LANGUAGES = 5;
    static final int W_LENGTH = 8;
    static final int W_ACTION_VERBS = 10;
    static final int W_METRICS = 10;
    static final int W_DATES = 8;
    static final int W_SKILLS_RECOGNIZED = 8;
    static final int W_LONG_BULLETS = 6;
    static final int W_PRONOUNS = 6;

    /** Część wagi przyznawana za status „warning”. */
    static final double WARNING_CREDIT = 0.5;

    // --- progi ---
    /** Poniżej — CV za krótkie (błąd). */
    static final int WORDS_ERROR_MIN = 150;
    /** Poniżej — CV raczej za krótkie (uwaga). */
    static final int WORDS_WARNING_MIN = 250;
    /** Powyżej — CV raczej za długie, ok. 2 strony (uwaga). */
    static final int WORDS_WARNING_MAX = 900;
    /** Powyżej — CV za długie (błąd). */
    static final int WORDS_ERROR_MAX = 1300;
    /** Udział punktów z czasownikiem akcji (rzeczownik odczasownikowy liczy się za pół). */
    static final double VERBS_OK_RATIO = 0.6;
    static final double VERBS_WARNING_RATIO = 0.3;
    /** Liczba punktów z mierzalnym efektem (liczba, %, kwota). */
    static final int METRICS_OK = 3;
    static final int METRICS_WARNING = 1;
    /** Liczba umiejętności rozpoznanych przez słownik. */
    static final int SKILLS_OK = 8;
    static final int SKILLS_WARNING = 3;
    /** Maksymalna długość punktu i akapitu (w słowach). */
    static final int BULLET_MAX_WORDS = 35;
    static final int PARAGRAPH_MAX_WORDS = 70;
    static final int LONG_ERROR_COUNT = 3;
    /** Liczba zaimków „ja/mój/I/my”. */
    static final int PRONOUNS_WARNING = 4;
    static final int PRONOUNS_ERROR = 8;

    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    static final Pattern PHONE_CANDIDATE = Pattern.compile("(?<![\\w])(?:\\+|00)?\\d[\\d \\-()]{7,16}\\d(?![\\w])");
    static final Pattern DATE_RANGE = Pattern.compile(
            "(?:19|20)\\d{2}\\s*(?:-|–|—|do|to|until)\\s*(?:(?:[a-z]{3,12}\\.?\\s+)?(?:(?:0?[1-9]|1[0-2])[./])?"
                    + "(?:19|20)\\d{2}|obecnie|teraz|nadal|now|present|current|dzis|aktualnie)"
                    + "|(?:od|since)\\s+(?:[a-z]{3,12}\\.?\\s+)?(?:(?:0?[1-9]|1[0-2])[./])?(?:19|20)\\d{2}");
    static final Pattern METRIC = Pattern.compile(
            "\\d+(?:[.,]\\d+)?\\s*(?:%|proc\\w*|x\\b|×|zl\\b|pln\\b|eur\\b|usd\\b|k\\b|tys\\w*|mln\\b|mld\\b|"
                    + "osob\\w*|uzytkownik\\w*|klient\\w*|users?\\b|customers?\\b|clients?\\b|ms\\b|sek\\w*|"
                    + "min\\b|minut\\w*|godz\\w*|h\\b|dni\\b|days?\\b|hours?\\b|razy|times\\b|zespol\\w*|people|"
                    + "engineers?\\b|developer\\w*|request\\w*|zapytan\\w*|transakcj\\w*|zamowien\\w*|rekord\\w*)"
                    + "|[$€£]\\s?\\d"
                    + "|\\b(?:dwukrotnie|trzykrotnie|czterokrotnie|o polowe|doubled|tripled|halved)\\b");

    /** Rdzenie czasowników w czasie przeszłym (bez końcówek -łem/-łam/-ł/-ła/-li/-ły), postać złożona. */
    static final List<String> PL_PAST_STEMS = List.of(
            "wdrozy", "wdraza", "zaprojektowa", "projektowa", "zoptymalizowa", "optymalizowa", "stworzy", "tworzy",
            "wspoltworzy", "zbudowa", "budowa", "rozbudowa", "opracowa", "poprowadzi", "prowadzi", "kierowa",
            "zarzadza", "zarzadzi", "zwiekszy", "zmniejszy", "skroci", "obnizy", "podnios", "usprawni", "zmigrowa",
            "migrowa", "przeprowadzi", "rozwija", "rozwina", "rozwine", "wprowadzi", "uruchomi", "dostarczy",
            "dostarcza", "zaimplementowa", "implementowa", "przygotowa", "koordynowa", "nadzorowa", "przeszkoli",
            "szkoli", "zintegrowa", "integrowa", "zredukowa", "redukowa", "osiagna", "osiagne", "zautomatyzowa",
            "automatyzowa", "zrefaktoryzowa", "refaktoryzowa", "przyspieszy", "zaoszczedzi", "pozyska",
            "pozyskiwa", "wygenerowa", "zainicjowa", "inicjowa", "utrzymywa", "utrzyma", "wynegocjowa",
            "negocjowa", "zorganizowa", "organizowa", "przeanalizowa", "analizowa", "przetestowa", "testowa",
            "napisa", "wspiera", "wspar", "mentorowa", "doradza", "zdoby", "wygra", "obsluzy", "obslugiwa",
            "odpowiada", "zrealizowa", "realizowa", "wspolpracowa", "zmodernizowa", "modernizowa", "zdiagnozowa",
            "naprawi", "rozwiaza", "wyeliminowa", "ustandaryzowa", "skalowa", "przeskalowa", "zabezpieczy",
            "monitorowa", "raportowa", "sprzeda", "sprzedawa", "zrekrutowa", "rekrutowa", "zatrudni", "uczy",
            "nauczy", "skroci", "ulepszy", "zaplanowa", "planowa", "wdrozy");
    private static final Pattern PL_PAST = Pattern.compile(
            "^(?:" + String.join("|", PL_PAST_STEMS) + ")(?:lem|lam|l|la|li|ly|lismy|lysmy)$");
    /** Czasowniki w 1. osobie czasu teraźniejszego (postać złożona). */
    static final Set<String> PL_PRESENT = Set.of(
            "projektuje", "rozwijam", "tworze", "wdrazam", "prowadze", "zarzadzam", "buduje", "optymalizuje",
            "automatyzuje", "dostarczam", "koordynuje", "wspieram", "analizuje", "testuje", "implementuje",
            "utrzymuje", "mentoruje", "szkole", "odpowiadam", "realizuje", "wspolpracuje", "dbam", "rozwiazuje",
            "integruje", "kieruje", "nadzoruje", "organizuje", "sprzedaje", "negocjuje", "pozyskuje", "obsluguje",
            "przygotowuje", "opracowuje", "monitoruje", "raportuje", "migruje", "refaktoryzuje", "planuje");
    /** Czasowniki akcji po angielsku. */
    static final Set<String> EN_VERBS = Set.of(
            "led", "built", "designed", "developed", "implemented", "delivered", "reduced", "increased", "improved",
            "optimized", "optimised", "migrated", "automated", "launched", "created", "managed", "architected",
            "mentored", "owned", "drove", "shipped", "streamlined", "introduced", "achieved", "coordinated",
            "spearheaded", "refactored", "scaled", "deployed", "established", "negotiated", "trained", "resolved",
            "integrated", "maintained", "wrote", "authored", "analyzed", "analysed", "tested", "supported",
            "organized", "organised", "initiated", "founded", "grew", "cut", "saved", "won", "sold", "handled",
            "supervised", "oversaw", "headed", "ran", "engineered", "modernized", "modernised", "accelerated",
            "boosted", "generated", "secured", "transformed", "consolidated", "planned");
    /** Rzeczownik odczasownikowy na początku punktu („Wdrożenie…”, „Migracja…”) — słabszy niż czasownik. */
    private static final Pattern NOMINAL = Pattern.compile(
            "^(?:rozwoj|budowa|obsluga|analiza|[a-z]{3,}(?:anie|enie|cja)|[a-z]{3,}ing)$");
    private static final Pattern PL_PRONOUN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:ja|mnie|moj|moja|moje|mojego|mojej|moim|moich|moimi)(?![\\p{L}\\p{N}])");
    private static final Pattern EN_PRONOUN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:I(?=\\s+(?:am|was|have|had|will|would|can|could|led|built|[a-z]{2,}ed)\\b)"
                    + "|I'(?:m|ve|d|ll)|[Mm]e|[Mm]y|[Mm]ine|[Mm]yself)(?![\\p{L}\\p{N}'])");

    private final SkillDictionary dictionary;
    private final ProfileService profiles;

    public CvAuditService(SkillDictionary dictionary, ProfileService profiles) {
        this.dictionary = dictionary;
        this.profiles = profiles;
    }

    /** Status kontroli (w JSON: „ok”, „warning”, „error”). */
    public enum CheckStatus {
        OK, WARNING, ERROR;

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }

        double credit() {
            return switch (this) {
                case OK -> 1.0;
                case WARNING -> WARNING_CREDIT;
                case ERROR -> 0.0;
            };
        }
    }

    /**
     * Jedna kontrola audytu.
     *
     * @param code     identyfikator, np. „contact_email”
     * @param status   ok / warning / error
     * @param title    nazwa kontroli
     * @param details  co znaleźliśmy
     * @param fix      jak poprawić (null, gdy ok)
     * @param weight   waga w wyniku
     * @param points   zdobyte punkty
     * @param examples dosłowne fragmenty CV ilustrujące problem
     */
    public record AuditCheck(String code, CheckStatus status, String title, String details, String fix, int weight,
                             double points, List<String> examples) {
    }

    public record AuditStats(int words, int bullets, List<String> sections, List<String> recognizedSkills) {
    }

    public record CvAudit(Long cvId, String cvName, int score, String grade, List<AuditCheck> checks,
                         AuditStats stats) {
    }

    public CvAudit audit(Long cvId) {
        CvVariant cv = profiles.cv(cvId);
        CvAudit a = audit(cv.getContent());
        return new CvAudit(cv.getId(), cv.getName(), a.score(), a.grade(), a.checks(), a.stats());
    }

    /** Audyt dowolnego tekstu CV (logika bez dostępu do bazy). */
    public CvAudit audit(String cvText) {
        String text = Text.nullToEmpty(cvText);
        CvStructure cv = CvStructure.parse(text);
        String folded = Text.fold(text);
        int words = CvTextNormalizer.wordCount(text);
        List<String> recognized = CvSkillExtractor.reliableHits(dictionary, text).stream()
                .map(SkillHit::skill).distinct().toList();
        List<Line> achievements = achievementLines(cv);

        List<AuditCheck> checks = new ArrayList<>();
        checks.add(email(text));
        checks.add(phone(text));
        checks.add(experienceSection(cv));
        checks.add(skillsSection(cv, recognized.size()));
        checks.add(educationSection(cv));
        checks.add(languages(cv, recognized));
        checks.add(length(words));
        checks.add(actionVerbs(achievements));
        checks.add(metrics(achievements.isEmpty() ? contentLines(cv) : achievements));
        checks.add(dates(cv));
        checks.add(skillsRecognized(recognized));
        checks.add(longBullets(cv));
        checks.add(pronouns(text, folded));

        double sum = checks.stream().mapToDouble(AuditCheck::points).sum();
        int total = checks.stream().mapToInt(AuditCheck::weight).sum();
        int score = total == 0 ? 0 : (int) Math.round(100.0 * sum / total);
        List<String> sections = cv.lines().stream().filter(Line::heading).map(Line::text).toList();
        return new CvAudit(null, null, score, grade(score), checks,
                new AuditStats(words, cv.bullets().size(), sections, recognized));
    }

    static String grade(int score) {
        if (score >= 85) {
            return "Bardzo dobrze przygotowane CV";
        }
        if (score >= 70) {
            return "Dobre CV — kilka rzeczy do poprawy";
        }
        if (score >= 50) {
            return "CV wymaga poprawek";
        }
        return "CV wymaga dużych zmian";
    }

    // ------------------------------------------------------------------ kontrole

    private static AuditCheck email(String text) {
        Matcher m = EMAIL.matcher(text);
        if (m.find()) {
            return ok("contact_email", "Adres e-mail", W_EMAIL, "Jest adres e-mail: " + m.group() + ".");
        }
        return check("contact_email", CheckStatus.ERROR, "Adres e-mail", W_EMAIL,
                "Nie znaleziono adresu e-mail.",
                "Dodaj adres e-mail w nagłówku CV — to podstawowy kanał kontaktu rekrutera.", List.of());
    }

    private static AuditCheck phone(String text) {
        Matcher m = PHONE_CANDIDATE.matcher(text);
        while (m.find()) {
            int digits = (int) m.group().chars().filter(Character::isDigit).count();
            if (digits >= 9 && digits <= 13) {
                return ok("contact_phone", "Numer telefonu", W_PHONE, "Jest numer telefonu: " + m.group().strip() + ".");
            }
        }
        return check("contact_phone", CheckStatus.WARNING, "Numer telefonu", W_PHONE,
                "Nie znaleziono numeru telefonu.",
                "Dodaj numer telefonu — wielu rekruterów dzwoni przed zaproszeniem na rozmowę.", List.of());
    }

    private static AuditCheck experienceSection(CvStructure cv) {
        if (cv.hasSection(SectionType.EXPERIENCE)) {
            return ok("section_experience", "Sekcja „Doświadczenie”", W_EXPERIENCE,
                    "Jest sekcja doświadczenia: „" + cv.heading(SectionType.EXPERIENCE).text() + "”.");
        }
        return check("section_experience", CheckStatus.ERROR, "Sekcja „Doświadczenie”", W_EXPERIENCE,
                "Nie znaleziono nagłówka sekcji doświadczenia zawodowego.",
                "Dodaj nagłówek „Doświadczenie zawodowe” (lub „Experience”) — systemy ATS szukają standardowych "
                        + "nazw sekcji.", List.of());
    }

    private static AuditCheck skillsSection(CvStructure cv, int recognized) {
        if (cv.hasSection(SectionType.SKILLS)) {
            return ok("section_skills", "Sekcja „Umiejętności”", W_SKILLS_SECTION,
                    "Jest sekcja umiejętności: „" + cv.heading(SectionType.SKILLS).text() + "”.");
        }
        boolean inText = recognized >= SKILLS_OK;
        return check("section_skills", inText ? CheckStatus.WARNING : CheckStatus.ERROR, "Sekcja „Umiejętności”",
                W_SKILLS_SECTION,
                inText ? "Umiejętności są w treści, ale bez osobnej sekcji."
                        : "Nie znaleziono sekcji umiejętności.",
                "Dodaj sekcję „Umiejętności” z listą technologii i narzędzi oddzielonych przecinkami — ATS "
                        + "porównuje ją ze słowami kluczowymi oferty.", List.of());
    }

    private static AuditCheck educationSection(CvStructure cv) {
        if (cv.hasSection(SectionType.EDUCATION)) {
            return ok("section_education", "Sekcja „Wykształcenie”", W_EDUCATION,
                    "Jest sekcja wykształcenia: „" + cv.heading(SectionType.EDUCATION).text() + "”.");
        }
        return check("section_education", CheckStatus.WARNING, "Sekcja „Wykształcenie”", W_EDUCATION,
                "Nie znaleziono sekcji wykształcenia.",
                "Dodaj sekcję „Wykształcenie” (uczelnia, kierunek, lata) — także kursy, jeśli nie masz studiów.",
                List.of());
    }

    private AuditCheck languages(CvStructure cv, List<String> recognized) {
        if (cv.hasSection(SectionType.LANGUAGES)) {
            return ok("section_languages", "Języki obce", W_LANGUAGES,
                    "Jest sekcja języków: „" + cv.heading(SectionType.LANGUAGES).text() + "”.");
        }
        List<String> langs = recognized.stream()
                .filter(n -> dictionary.get(n).map(Skill::isLanguage).orElse(false)).toList();
        if (!langs.isEmpty()) {
            return ok("section_languages", "Języki obce", W_LANGUAGES,
                    "Języki są wymienione w treści: " + String.join(", ", langs) + ".");
        }
        return check("section_languages", CheckStatus.WARNING, "Języki obce", W_LANGUAGES,
                "Nie znaleziono informacji o językach obcych.",
                "Dodaj sekcję „Języki” z poziomem (np. „Angielski — B2”). Wiele ofert wymaga konkretnego poziomu.",
                List.of());
    }

    private static AuditCheck length(int words) {
        String details = "CV ma " + words + " " + Plural.of(words, "słowo", "słowa", "słów") + ".";
        if (words < WORDS_ERROR_MIN) {
            return check("length", CheckStatus.ERROR, "Długość CV", W_LENGTH, details + " To za mało, by pokazać "
                            + "doświadczenie.",
                    "Opisz każde stanowisko w 3–5 punktach (obowiązki i efekty); celuj w " + WORDS_WARNING_MIN + "–"
                            + WORDS_WARNING_MAX + " słów.", List.of());
        }
        if (words < WORDS_WARNING_MIN) {
            return check("length", CheckStatus.WARNING, "Długość CV", W_LENGTH, details + " CV jest dość krótkie.",
                    "Rozwiń opis doświadczenia o konkretne projekty i efekty; celuj w " + WORDS_WARNING_MIN + "–"
                            + WORDS_WARNING_MAX + " słów.", List.of());
        }
        if (words > WORDS_ERROR_MAX) {
            return check("length", CheckStatus.ERROR, "Długość CV", W_LENGTH, details + " To znacznie więcej niż "
                            + "2 strony.",
                    "Skróć CV do najważniejszych 10 lat i osiągnięć związanych z rolą; usuń powtórzenia.", List.of());
        }
        if (words > WORDS_WARNING_MAX) {
            return check("length", CheckStatus.WARNING, "Długość CV", W_LENGTH, details + " CV może przekraczać "
                            + "2 strony.",
                    "Skróć opisy starszych stanowisk i usuń mniej istotne punkty.", List.of());
        }
        return ok("length", "Długość CV", W_LENGTH, details + " Długość odpowiednia (" + WORDS_WARNING_MIN + "–"
                + WORDS_WARNING_MAX + ").");
    }

    private static AuditCheck actionVerbs(List<Line> achievements) {
        if (achievements.isEmpty()) {
            return check("action_verbs", CheckStatus.WARNING, "Czasowniki akcji", W_ACTION_VERBS,
                    "Nie znaleziono punktów opisujących obowiązki i osiągnięcia.",
                    "Opisz doświadczenie w punktach zaczynających się od czasownika, np. „Wdrożyłam…”, "
                            + "„Zaprojektowałem…”, „Skróciłam…”, „Led…”, „Built…”.", List.of());
        }
        int strong = 0;
        int weak = 0;
        List<String> nominalExamples = new ArrayList<>();
        for (Line l : achievements) {
            ActionKind kind = actionKind(l.body());
            if (kind == ActionKind.VERB) {
                strong++;
            } else if (kind == ActionKind.NOMINAL) {
                weak++;
                if (nominalExamples.size() < 3) {
                    nominalExamples.add(prefix(l.body(), 8));
                }
            } else if (nominalExamples.size() < 3) {
                nominalExamples.add(prefix(l.body(), 8));
            }
        }
        int n = achievements.size();
        double ratio = (strong + 0.5 * weak) / n;
        String details = strong + " z " + n + " " + Plural.of(n, "punktu", "punktów", "punktów")
                + " zawiera czasownik akcji"
                + (weak > 0 ? ", " + weak + " " + Plural.of(weak, "zaczyna", "zaczynają", "zaczyna")
                + " się od rzeczownika (np. „Wdrożenie…”)" : "") + ".";
        CheckStatus status = ratio >= VERBS_OK_RATIO ? CheckStatus.OK
                : ratio >= VERBS_WARNING_RATIO ? CheckStatus.WARNING : CheckStatus.ERROR;
        return check("action_verbs", status, "Czasowniki akcji", W_ACTION_VERBS, details,
                status == CheckStatus.OK ? null
                        : "Zaczynaj punkty od czasownika w 1. osobie: „Wdrożyłam/Wdrożyłem”, „Zaprojektowałem”, "
                        + "„Zoptymalizowałam”, „Skróciłem” (po angielsku: „Led”, „Built”, „Reduced”) — "
                        + "brzmi konkretniej niż „Wdrożenie…”.",
                status == CheckStatus.OK ? List.of() : nominalExamples);
    }

    enum ActionKind {
        VERB, NOMINAL, NONE
    }

    /** Czy linia zawiera czasownik akcji (gdziekolwiek), czy zaczyna się od rzeczownika odczasownikowego. */
    static ActionKind actionKind(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher m = CvStructure.WORD.matcher(Text.fold(line));
        while (m.find()) {
            tokens.add(m.group());
        }
        for (String t : tokens) {
            if (PL_PAST.matcher(t).matches() || PL_PRESENT.contains(t) || EN_VERBS.contains(t)) {
                return ActionKind.VERB;
            }
        }
        if (!tokens.isEmpty() && NOMINAL.matcher(tokens.get(0)).matches()) {
            return ActionKind.NOMINAL;
        }
        return ActionKind.NONE;
    }

    private static AuditCheck metrics(List<Line> lines) {
        int count = 0;
        List<String> examples = new ArrayList<>();
        for (Line l : lines) {
            String body = l.body();
            Matcher m = METRIC.matcher(Text.fold(body));
            if (m.find()) {
                count++;
                if (examples.size() < 3) {
                    // pozycje w tekście złożonym = pozycje w oryginale (fold zachowuje długość)
                    examples.add(snippet(body, m.start(), m.end()));
                }
            }
        }
        String details = count == 0 ? "Nie znaleziono mierzalnych efektów (liczb, procentów, kwot)."
                : "Mierzalne efekty w " + count + " " + Plural.of(count, "punkcie", "punktach", "punktach") + ".";
        CheckStatus status = count >= METRICS_OK ? CheckStatus.OK
                : count >= METRICS_WARNING ? CheckStatus.WARNING : CheckStatus.ERROR;
        return check("measurable_results", status, "Mierzalne efekty", W_METRICS, details,
                status == CheckStatus.OK ? null
                        : "Dodaj liczby do co najmniej " + METRICS_OK + " punktów: o ile procent coś przyspieszyło, "
                        + "ilu użytkowników obsługuje system, jaki budżet lub zespół prowadziłaś/eś. Podawaj tylko "
                        + "prawdziwe, możliwe do obrony wartości.",
                examples);
    }

    private static AuditCheck dates(CvStructure cv) {
        if (cv.hasSection(SectionType.EXPERIENCE)) {
            List<String> found = new ArrayList<>();
            for (Line l : cv.linesIn(SectionType.EXPERIENCE)) {
                Matcher m = DATE_RANGE.matcher(Text.fold(l.text()));
                if (m.find()) {
                    found.add(l.text().substring(m.start(), m.end()));
                }
            }
            if (!found.isEmpty()) {
                return ok("experience_dates", "Daty zatrudnienia", W_DATES, "Przy doświadczeniu są daty ("
                        + found.size() + " " + Plural.of(found.size(), "okres", "okresy", "okresów") + ", np. „"
                        + found.get(0) + "”).");
            }
            return check("experience_dates", CheckStatus.ERROR, "Daty zatrudnienia", W_DATES,
                    "W sekcji doświadczenia nie znaleziono dat (np. „2021 – 2023”, „03.2022 – obecnie”).",
                    "Przy każdym stanowisku podaj okres zatrudnienia (miesiąc i rok) — ATS i rekruterzy liczą na "
                            + "tej podstawie staż.", List.of());
        }
        boolean any = cv.lines().stream().filter(l -> l.section() != SectionType.EDUCATION)
                .anyMatch(l -> DATE_RANGE.matcher(Text.fold(l.text())).find());
        return check("experience_dates", any ? CheckStatus.WARNING : CheckStatus.ERROR, "Daty zatrudnienia",
                W_DATES, any ? "W CV są okresy dat, ale bez wyraźnej sekcji doświadczenia."
                        : "Nie znaleziono dat zatrudnienia.",
                "Przy każdym stanowisku podaj okres zatrudnienia, np. „03.2022 – obecnie”.", List.of());
    }

    private static AuditCheck skillsRecognized(List<String> recognized) {
        int n = recognized.size();
        String list = String.join(", ", recognized.subList(0, Math.min(12, n))) + (n > 12 ? "…" : "");
        String details = n == 0 ? "Słownik nie rozpoznał w CV żadnej umiejętności."
                : "Rozpoznane umiejętności (" + n + "): " + list + ".";
        CheckStatus status = n >= SKILLS_OK ? CheckStatus.OK : n >= SKILLS_WARNING ? CheckStatus.WARNING
                : CheckStatus.ERROR;
        return check("skills_recognized", status, "Słowa kluczowe (umiejętności)", W_SKILLS_RECOGNIZED, details,
                status == CheckStatus.OK ? null
                        : "Wpisz nazwy technologii i narzędzi w standardowej formie (np. „PostgreSQL”, „Docker”, "
                        + "„Scrum”) — ATS dopasowuje słowa kluczowe dosłownie.", List.of());
    }

    private static AuditCheck longBullets(CvStructure cv) {
        List<String> examples = new ArrayList<>();
        int count = 0;
        for (Line l : cv.lines()) {
            if (l.heading()) {
                continue;
            }
            int w = CvTextNormalizer.wordCount(l.text());
            if ((l.bullet() && w > BULLET_MAX_WORDS) || (!l.bullet() && w > PARAGRAPH_MAX_WORDS)) {
                count++;
                if (examples.size() < 3) {
                    examples.add(prefix(l.body(), 10));
                }
            }
        }
        if (count == 0) {
            return ok("long_bullets", "Zwięzłe punkty", W_LONG_BULLETS, "Punkty mają do " + BULLET_MAX_WORDS
                    + " słów, akapity do " + PARAGRAPH_MAX_WORDS + ".");
        }
        return check("long_bullets", count >= LONG_ERROR_COUNT ? CheckStatus.ERROR : CheckStatus.WARNING,
                "Zwięzłe punkty", W_LONG_BULLETS,
                "Za długie fragmenty: " + count + " (punkt > " + BULLET_MAX_WORDS + " słów lub akapit > "
                        + PARAGRAPH_MAX_WORDS + " słów).",
                "Podziel długie punkty na krótsze: jeden punkt = jedno działanie i jego efekt (1–2 linie).", examples);
    }

    private static AuditCheck pronouns(String text, String folded) {
        List<int[]> hits = new ArrayList<>();
        Matcher pl = PL_PRONOUN.matcher(folded);
        while (pl.find()) {
            hits.add(new int[]{pl.start(), pl.end()});
        }
        Matcher en = EN_PRONOUN.matcher(text);
        while (en.find()) {
            hits.add(new int[]{en.start(), en.end()});
        }
        int n = hits.size();
        if (n < PRONOUNS_WARNING) {
            return ok("first_person", "Zaimki „ja”", W_PRONOUNS, n == 0 ? "Brak zbędnych zaimków „ja/mój”."
                    : "Zaimki „ja/mój” pojawiają się rzadko (" + n + ").");
        }
        hits.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<String> examples = new ArrayList<>();
        for (int[] h : hits.subList(0, Math.min(3, n))) {
            examples.add(snippet(text, h[0], h[1]));
        }
        return check("first_person", n >= PRONOUNS_ERROR ? CheckStatus.ERROR : CheckStatus.WARNING,
                "Zaimki „ja”", W_PRONOUNS,
                "Zaimki „ja/mój/mnie” (lub „I/my/me”) występują " + n + " razy.",
                "Usuń zaimki — forma czasownika już mówi, kto działał: zamiast „Ja odpowiadałem za…” napisz "
                        + "„Odpowiadałem za…”.", examples);
    }

    // ------------------------------------------------------------------ pomocnicze

    /** Punkty opisujące obowiązki i osiągnięcia: wypunktowania w doświadczeniu/projektach (albo wszystkie). */
    static List<Line> achievementLines(CvStructure cv) {
        boolean hasSections = cv.hasSection(SectionType.EXPERIENCE) || cv.hasSection(SectionType.PROJECTS);
        List<Line> bullets = cv.bullets().stream()
                .filter(l -> !hasSections || l.section() == SectionType.EXPERIENCE
                        || l.section() == SectionType.PROJECTS)
                .toList();
        if (!bullets.isEmpty()) {
            return bullets;
        }
        if (!cv.hasSection(SectionType.EXPERIENCE)) {
            return List.of();
        }
        // doświadczenie opisane zdaniami zamiast punktów: linie z co najmniej 6 słowami i bez dat
        return cv.linesIn(SectionType.EXPERIENCE).stream()
                .filter(l -> CvTextNormalizer.wordCount(l.text()) >= 6)
                .filter(l -> !DATE_RANGE.matcher(Text.fold(l.text())).find())
                .toList();
    }

    private static List<Line> contentLines(CvStructure cv) {
        return cv.lines().stream().filter(l -> !l.heading()).toList();
    }

    /** Początek linii (pierwsze {@code words} słów) — dosłowny podciąg. */
    static String prefix(String line, int words) {
        Matcher m = CvStructure.WORD.matcher(line);
        int count = 0;
        int end = line.length();
        while (m.find()) {
            if (++count == words) {
                end = m.end();
                break;
            }
        }
        return line.substring(0, end).strip();
    }

    /** Fragment wokół [start, end) rozszerzony do granic słów (~40 znaków z każdej strony) — dosłowny podciąg. */
    static String snippet(String text, int start, int end) {
        int from = Math.max(0, start - 40);
        int to = Math.min(text.length(), end + 40);
        while (from > 0 && from < start && !Character.isWhitespace(text.charAt(from - 1))) {
            from++;
        }
        while (to < text.length() && to > end && !Character.isWhitespace(text.charAt(to))) {
            to--;
        }
        int nlBefore = text.lastIndexOf('\n', start - 1);
        if (nlBefore >= from) {
            from = nlBefore + 1;
        }
        int nlAfter = text.indexOf('\n', end);
        if (nlAfter >= 0 && nlAfter < to) {
            to = nlAfter;
        }
        return text.substring(from, to).strip();
    }

    private static AuditCheck ok(String code, String title, int weight, String details) {
        return check(code, CheckStatus.OK, title, weight, details, null, List.of());
    }

    private static AuditCheck check(String code, CheckStatus status, String title, int weight, String details,
                                    String fix, List<String> examples) {
        double points = Math.round(weight * status.credit() * 100.0) / 100.0;
        return new AuditCheck(code, status, title, details, status == CheckStatus.OK ? null : fix, weight, points,
                List.copyOf(examples));
    }
}

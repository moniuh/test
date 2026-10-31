package pl.radarpracy.interview;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heurystyczna ocena odpowiedzi metodą STAR (pomysł #54) — po polsku i po angielsku, bez AI.
 *
 * <p>Każdy element STAR ma listę wskaźników z wagami (2 — wyraźny, 1 — średni, 0,5 — słaby).
 * Dla każdego zdania liczymy sumę wag; element jest wykryty, gdy najlepsze zdanie ma ≥ 2 (pełna pewność)
 * albo ≥ 1 (słaby sygnał, 60% punktów). Cytatem-dowodem jest zawsze to zdanie — dosłowny fragment
 * odpowiedzi (sprawdzane w kodzie).</p>
 *
 * <p>Punkty (razem 100): sytuacja 15, zadanie 15, działanie 25, rezultat 20, liczby 10, długość 10,
 * „ja” zamiast „my” 5; minus do 10 pkt za nadmiar słów-wypełniaczy.</p>
 */
@Component
public class StarAnalyzer {

    static final int MIN_WORDS = 80;
    static final int MAX_WORDS = 350;
    static final int WORDS_PER_MINUTE = 130;

    private record Marker(Pattern pattern, double weight) {
    }

    record Sentence(String text, int start, String folded) {
    }

    private record Filler(String label, Pattern pattern) {
    }

    private record Detection(StarAssessment.StarPart part, double score) {
    }

    // --- wskaźniki (tekst złożony: małe litery, bez polskich znaków) ---

    private static final List<Marker> SITUATION = markers(
            2, List.of("w poprzedniej (?:pracy|firmie|roli)", "w obecnej (?:pracy|firmie|roli)",
                    "w (?:moim|naszym|jednym) (?:zespole|projekcie|z projektow)", "pewnego razu",
                    "(?:kilka|pare|dwa|trzy) (?:miesiecy|lat|lata|tygodni) temu", "rok temu", "w zeszlym roku",
                    "in my (?:previous|last|current|former) (?:job|role|company|team|position|project)",
                    "at my (?:previous|last|current|former)", "while (?:i was )?working", "when i was",
                    "last year", "(?:a few|several|two|three) (?:months|years|weeks) ago"),
            1, List.of("sytuacj", "kontekst", "problem", "klient", "awari", "incydent", "deadline", "termin",
                    "mielismy", "mialysmy", "situation", "context", "issue", "client", "customer", "outage",
                    "incident", "we had", "there was"),
            0.5, List.of("projek", "firm", "zespol", "kiedy\\b", "gdy\\b", "project", "company", "team\\b", "when\\b"));

    private static final List<Marker> TASK = markers(
            2, List.of("moim zadaniem", "mial(?:am|em) za zadanie", "odpowiadal(?:am|em) za", "byl(?:am|em) odpowiedzialn",
                    "celem bylo", "moim celem", "naszym celem", "musial(?:am|em)", "trzeba bylo", "nalezalo",
                    "zostal(?:am|em) poproszon", "my task", "my goal", "the goal was", "i was responsible",
                    "i was asked to", "i had to", "i needed to", "my job was", "my role was"),
            1, List.of("zadani", "cel\\b", "celu\\b", "wyzwani", "odpowiedzialn", "chodzilo o", "potrzeb", "task",
                    "goal", "objective", "challenge", "responsib", "needed to", "had to"),
            0.5, List.of("chcial(?:am|em)", "mial(?:am|em)\\b", "wanted"));

    private static final List<Marker> ACTION = markers(
            2, List.of("najpierw", "nastepnie", "w pierwszym kroku", "kolejnym krokiem", "zdecydowal(?:am|em)",
                    "postanowil(?:am|em)", "first,? i\\b", "then i\\b", "next,? i\\b",
                    "i (?:implemented|built|designed|analy[sz]ed|proposed|decided|led|created|wrote|organi[sz]ed|"
                            + "introduced|fixed|refactored|automated|set up|migrated|investigated|talked|convinced|"
                            + "prepared|ran|started|reviewed|added|changed|moved|negotiated)\\b"),
            1, List.of("wdroz", "zaimplement", "przeanaliz", "zaproponow", "zaprojekt", "zautomatyz", "zoptymaliz",
                    "napisal", "przygotowal", "zorganiz", "poprowadzil", "porozmawial", "przekonal", "naprawil",
                    "potem", "then\\b"),
            0.5, List.of("\\p{L}{3,}(?:lismy|lysmy)\\b"));

    private static final List<Marker> RESULT = markers(
            2, List.of("w efekcie", "w rezultacie", "dzieki temu", "udalo (?:mi |nam )?sie", "efektem", "rezultatem",
                    "w wyniku", "ostatecznie", "finalnie", "nauczyl(?:am|em) sie", "wnios(?:ek|ki)",
                    "as a result", "resulted in", "which (?:led|resulted)", "thanks to (?:this|that)", "in the end",
                    "i learned", "lesson"),
            1, List.of("skrocil", "zmniejszyl", "zwiekszyl", "wzrosl", "spadl", "poprawil", "przyspieszyl",
                    "zaoszczedz", "oszczedn", "osiagn", "zadowolen", "pochwal", "docenil", "efekt", "rezultat", "wynik",
                    "reduced", "increased", "improved", "saved", "achieved", "decreased", "grew", "faster",
                    "result", "outcome"),
            0.5, List.of("w koncu", "finally", "eventually"));

    /** Czasowniki w 1. os. lp. czasu przeszłego: „zrobiłam”, „wdrożyłem” (po złożeniu: -lam/-lem). */
    private static final Pattern SINGULAR_VERB = Pattern.compile("(?<!\\p{L})\\p{L}{2,}(?:lam|lem)(?!\\p{L})");
    private static final Pattern PLURAL_VERB = Pattern.compile("(?<!\\p{L})\\p{L}{2,}(?:lismy|lysmy)(?!\\p{L})");
    /** Rzeczowniki z końcówką -lam/-lem, które nie są czasownikami. */
    private static final Set<String> NOT_VERBS = Set.of("problem", "problemem", "reklam", "islam", "emblem",
            "dylem", "tlem", "stylem", "celem", "modelem", "kolem", "dzialem", "materialem", "kapitalem",
            "potencjalem", "portalem", "kanalem", "szpitalem", "lokalem", "personelem", "hotelem", "skalem");
    /** Czasowniki opisujące stan lub zadanie — nie liczą się jako działanie. */
    private static final Set<String> NOT_ACTIONS = Set.of("bylam", "bylem", "mialam", "mialem", "chcialam",
            "chcialem", "musialam", "musialem", "odpowiadalam", "odpowiadalem", "pracowalam", "pracowalem");

    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d])\\d+(?:[.,]\\d+)?(?:\\s?(?:%|proc\\.?|procent\\p{L}*|zł|zl|pln|k\\b|tys\\.?|mln|h\\b|"
                    + "godz\\p{L}*|min\\p{L}*|sek\\p{L}*|dni|dzie\\p{L}*|tygod\\p{L}*|miesi\\p{L}*|osób|osob\\p{L}*|"
                    + "razy|x\\b|hours?|days?|weeks?|months?|people|users?|ms\\b|s\\b))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMBER_WORDS = Pattern.compile(
            "(?<!\\p{L})(?:dwukrotnie|trzykrotnie|czterokrotnie|kilkukrotnie|o polowe|dwa razy|trzy razy|"
                    + "twice|half|doubled|tripled)(?!\\p{L})");
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?");

    private static final List<Filler> FILLERS = List.of(
            filler("yyy", "y{2,}"), filler("eee", "e{3,}"), filler("hmm", "h?m{2,}"), filler("jakby", "jakby"),
            filler("w sumie", "w sumie"), filler("generalnie", "generalnie"), filler("tak naprawdę", "tak naprawde"),
            filler("po prostu", "po prostu"), filler("wiesz", "wiesz"), filler("kurczę", "kurcze"),
            filler("w zasadzie", "w zasadzie"), filler("jakoś tak", "jakos tak"), filler("no więc", "no wiec"),
            filler("um", "u+m+"), filler("uh", "u+h+"), filler("you know", "you know"), filler("basically", "basically"),
            filler("actually", "actually"), filler("literally", "literally"), filler("kind of", "kind of"),
            filler("sort of", "sort of"), filler("i mean", "i mean"));

    private static final Set<String> PL_SINGULAR = Set.of("ja", "mnie", "mi", "moj", "moja", "moje", "mojego",
            "mojej", "moim", "moich", "mna");
    private static final Set<String> PL_PLURAL = Set.of("my", "nas", "nam", "nami", "nasz", "nasza", "nasze",
            "naszego", "naszej", "naszym", "naszych");
    private static final Set<String> EN_SINGULAR = Set.of("i", "me", "my", "mine", "myself", "i'm", "i've", "i'd");
    private static final Set<String> EN_PLURAL = Set.of("we", "us", "our", "ours", "ourselves", "we're", "we've");
    private static final Set<String> EN_HINTS = Set.of("the", "was", "were", "with", "that", "have", "had", "this",
            "it", "of", "is", "are", "our", "we", "and");
    private static final Set<String> PL_HINTS = Set.of("sie", "nie", "ze", "jest", "byl", "bylo", "byla", "oraz",
            "jak", "dla", "w", "na", "z", "do", "po");

    private final SkillDictionary dictionary;

    public StarAnalyzer(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Ocena odpowiedzi.
     *
     * @param answer         odpowiedź kandydata
     * @param offerSkills    umiejętności z wymagań oferty (puste — bez kontekstu oferty)
     * @param mustHaveSkills umiejętności z wymagań obowiązkowych
     */
    public StarAssessment analyze(String answer, Collection<String> offerSkills, Collection<String> mustHaveSkills) {
        String text = answer == null ? "" : answer;
        List<Sentence> sentences = sentences(text);
        boolean english = isEnglish(text);

        Detection situation = detect(sentences, SITUATION, Part.SITUATION);
        Detection task = detect(sentences, TASK, Part.TASK);
        Detection action = detect(sentences, ACTION, Part.ACTION);
        Detection result = detect(sentences, RESULT, Part.RESULT);

        List<String> numbers = numbers(text);
        boolean hasNumbers = !numbers.isEmpty();
        int words = wordCount(text);
        String length = words < MIN_WORDS ? "TOO_SHORT" : words > MAX_WORDS ? "TOO_LONG" : "OK";
        double minutes = Math.round(10.0 * words / WORDS_PER_MINUTE) / 10.0;
        StarAssessment.PersonUsage person = person(text, english);
        List<StarAssessment.FillerCount> fillers = fillers(text);
        int fillerTotal = fillers.stream().mapToInt(StarAssessment.FillerCount::count).sum();

        // --- wynik
        List<StarAssessment.ScorePart> parts = new ArrayList<>();
        parts.add(new StarAssessment.ScorePart("Sytuacja", 15 * situation.part().confidence(), 15));
        parts.add(new StarAssessment.ScorePart("Zadanie", 15 * task.part().confidence(), 15));
        parts.add(new StarAssessment.ScorePart("Działanie", 25 * action.part().confidence(), 25));
        parts.add(new StarAssessment.ScorePart("Rezultat", 20 * result.part().confidence(), 20));
        parts.add(new StarAssessment.ScorePart("Liczby i miary", hasNumbers ? 10 : 0, 10));
        double lengthPoints = switch (length) {
            case "OK" -> 10;
            case "TOO_LONG" -> 5;
            default -> Math.min(9, Math.round(10.0 * words / MIN_WORDS));
        };
        parts.add(new StarAssessment.ScorePart("Długość", lengthPoints, 10));
        double personPoints = switch (person.verdict()) {
            case "SINGULAR" -> 5;
            case "BALANCED" -> 3;
            default -> 0;
        };
        parts.add(new StarAssessment.ScorePart("Twój wkład („ja”)", personPoints, 5));
        double allowed = 1 + words / 100.0;
        double penalty = Math.min(10, 2 * Math.max(0, Math.floor(fillerTotal - allowed)));
        if (penalty > 0) {
            parts.add(new StarAssessment.ScorePart("Słowa-wypełniacze", -penalty, 0));
        }
        double sum = parts.stream().mapToDouble(StarAssessment.ScorePart::points).sum();
        int score = (int) Math.max(0, Math.min(100, Math.round(sum)));

        // --- umiejętności z oferty
        Set<String> inAnswer = new LinkedHashSet<>(dictionary.skillsIn(text));
        for (String s : new ArrayList<>(inAnswer)) {
            inAnswer.addAll(dictionary.impliedBy(s));
        }
        List<String> mentioned = offerSkills == null ? List.of()
                : offerSkills.stream().filter(inAnswer::contains).distinct().toList();
        List<String> missing = mustHaveSkills == null ? List.of()
                : mustHaveSkills.stream().filter(s -> !inAnswer.contains(s)).filter(this::isTechnical).distinct().toList();

        List<String> feedback = feedback(situation, task, action, result, hasNumbers, numbers, words, length, minutes,
                person, fillers, fillerTotal, allowed, offerSkills, mentioned, missing);
        return new StarAssessment(score, new StarAssessment.Star(situation.part(), task.part(), action.part(),
                result.part()), hasNumbers, numbers, words, length, minutes, person, fillers, fillerTotal, mentioned,
                missing, feedback, parts, null, "offline", null);
    }

    private enum Part { SITUATION, TASK, ACTION, RESULT }

    private Detection detect(List<Sentence> sentences, List<Marker> markers, Part part) {
        double best = 0;
        Sentence bestSentence = null;
        List<String> bestMarkers = List.of();
        int n = sentences.size();
        for (int i = 0; i < n; i++) {
            Sentence s = sentences.get(i);
            double score = 0;
            List<String> found = new ArrayList<>();
            for (Marker m : markers) {
                Matcher matcher = m.pattern().matcher(s.folded());
                if (matcher.find()) {
                    score += m.weight();
                    found.add(s.text().substring(matcher.start(), matcher.end()));
                }
            }
            if (part == Part.ACTION) {
                int verbs = 0;
                Matcher v = SINGULAR_VERB.matcher(s.folded());
                while (v.find() && verbs < 3) {
                    String word = v.group();
                    if (!NOT_VERBS.contains(word) && !NOT_ACTIONS.contains(word)) {
                        verbs++;
                        found.add(s.text().substring(v.start(), v.end()));
                    }
                }
                score += verbs;
            }
            if (score > 0) {
                if (part == Part.SITUATION && n >= 3 && i < Math.ceil(n / 3.0)) {
                    score += 0.5;
                }
                if (part == Part.RESULT) {
                    if (!numbers(s.text()).isEmpty()) {
                        score += 1;
                    }
                    if (n >= 3 && i >= n - Math.ceil(n / 3.0)) {
                        score += 0.5;
                    }
                }
            }
            if (score > best) {
                best = score;
                bestSentence = s;
                bestMarkers = found;
            }
        }
        double confidence = best >= 2 ? 1.0 : best >= 1 ? 0.6 : 0;
        String evidence = confidence > 0 && bestSentence != null ? bestSentence.text() : null;
        return new Detection(new StarAssessment.StarPart(confidence > 0, confidence, evidence,
                confidence > 0 ? distinctMarkers(bestMarkers) : List.of()), best);
    }

    /** Wskaźniki bez powtórzeń i bez fragmentów zawartych w dłuższych („zadani” w „Moim zadaniem”). */
    static List<String> distinctMarkers(List<String> markers) {
        List<String> unique = List.copyOf(new LinkedHashSet<>(markers));
        List<String> out = new ArrayList<>();
        for (String m : unique) {
            String f = Text.fold(m);
            boolean contained = unique.stream().anyMatch(o -> o.length() > m.length() && Text.fold(o).contains(f));
            if (!contained) {
                out.add(m);
            }
        }
        return out;
    }

    /** Zdania z pozycjami w oryginale; każde jest dosłownym podciągiem odpowiedzi. */
    static List<Sentence> sentences(String text) {
        List<Sentence> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            boolean end = i == text.length();
            if (!end) {
                char c = text.charAt(i);
                boolean punct = c == '!' || c == '?' || c == '…' || c == '\n'
                        || (c == '.' && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))));
                if (!punct) {
                    continue;
                }
            }
            int to = end ? i : i + 1;
            String raw = text.substring(start, to);
            String stripped = raw.strip();
            if (stripped.length() >= 3) {
                int offset = start + raw.indexOf(stripped);
                out.add(new Sentence(stripped, offset, Text.fold(stripped)));
            }
            start = to;
        }
        return out;
    }

    static List<String> numbers(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String hit = m.group().strip();
            if (YEAR.matcher(hit).matches()) {
                continue;
            }
            out.add(hit);
        }
        String folded = Text.fold(text);
        Matcher w = NUMBER_WORDS.matcher(folded);
        while (w.find()) {
            out.add(text.substring(w.start(), w.end()));
        }
        return List.copyOf(out);
    }

    static int wordCount(String text) {
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    static boolean isEnglish(String text) {
        int en = 0;
        int pl = 0;
        for (String w : words(Text.fold(text))) {
            if (EN_HINTS.contains(w)) {
                en++;
            }
            if (PL_HINTS.contains(w)) {
                pl++;
            }
        }
        return en > pl;
    }

    static StarAssessment.PersonUsage person(String text, boolean english) {
        String folded = Text.fold(text);
        int singular = 0;
        int plural = 0;
        for (String w : words(folded)) {
            if (english) {
                singular += EN_SINGULAR.contains(w) ? 1 : 0;
                plural += EN_PLURAL.contains(w) ? 1 : 0;
            } else {
                singular += PL_SINGULAR.contains(w) ? 1 : 0;
                plural += PL_PLURAL.contains(w) ? 1 : 0;
            }
        }
        if (!english) {
            Matcher v = SINGULAR_VERB.matcher(folded);
            while (v.find()) {
                singular += NOT_VERBS.contains(v.group()) ? 0 : 1;
            }
            Matcher p = PLURAL_VERB.matcher(folded);
            while (p.find()) {
                plural++;
            }
        }
        int total = singular + plural;
        String verdict;
        if (total == 0) {
            verdict = "NONE";
        } else if (singular >= 0.6 * total) {
            verdict = "SINGULAR";
        } else if (singular <= 0.4 * total) {
            verdict = "PLURAL";
        } else {
            verdict = "BALANCED";
        }
        return new StarAssessment.PersonUsage(singular, plural, verdict);
    }

    static List<StarAssessment.FillerCount> fillers(String text) {
        String folded = Text.fold(text);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Filler f : FILLERS) {
            Matcher m = f.pattern().matcher(folded);
            int c = 0;
            while (m.find()) {
                c++;
            }
            if (c > 0) {
                counts.merge(f.label(), c, Integer::sum);
            }
        }
        List<StarAssessment.FillerCount> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(new StarAssessment.FillerCount(k, v)));
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    private boolean isTechnical(String skill) {
        return dictionary.get(skill).map(s -> !"miekkie".equals(s.category()) && !"wyksztalcenie".equals(s.category())
                && !"jezyk-obcy".equals(s.category())).orElse(true);
    }

    private static List<String> feedback(Detection s, Detection t, Detection a, Detection r, boolean hasNumbers,
                                         List<String> numbers, int words, String length, double minutes,
                                         StarAssessment.PersonUsage person, List<StarAssessment.FillerCount> fillers,
                                         int fillerTotal, double allowed, Collection<String> offerSkills,
                                         List<String> mentioned, List<String> missing) {
        List<String> out = new ArrayList<>();
        boolean full = s.part().confidence() == 1 && t.part().confidence() == 1 && a.part().confidence() == 1
                && r.part().confidence() == 1;
        if (full) {
            out.add("Pełna struktura STAR — sytuacja, zadanie, działania i rezultat są wyraźne.");
        }
        out.add(partFeedback(s.part(), "Brakuje sytuacji: zacznij od 1–2 zdań kontekstu — gdzie, kiedy i jaki był problem.",
                "Kontekst jest zarysowany słabo — dopowiedz, gdzie i kiedy to było i dlaczego było ważne."));
        out.add(partFeedback(t.part(), "Nie widać zadania: powiedz wprost, za co odpowiadasz i jaki był cel.",
                "Zadanie jest niejasne — dodaj zdanie „Moim zadaniem było…”."));
        out.add(partFeedback(a.part(), "Brakuje działań: opisz krok po kroku, co konkretnie zrobiono — w pierwszej osobie.",
                "Działania są opisane ogólnie — wymień 2–3 konkretne kroki, które wykonano."));
        out.add(partFeedback(r.part(), "Brakuje rezultatu: zakończ efektem (najlepiej mierzalnym) i wnioskiem.",
                "Rezultat jest słaby — dodaj, co się zmieniło dzięki Twoim działaniom."));
        if (hasNumbers) {
            out.add("Liczby w odpowiedzi (" + String.join(", ", numbers.stream().limit(4).map(n -> "„" + n + "”").toList())
                    + ") uwiarygadniają rezultat.");
        } else {
            out.add("Dodaj liczby: skalę, czas, procent poprawy, oszczędność — liczby uwiarygadniają odpowiedź.");
        }
        if ("TOO_SHORT".equals(length)) {
            out.add("Odpowiedź jest za krótka (" + words + " słów) — celuj w 150–300 słów, czyli około 1–2 minut.");
        } else if ("TOO_LONG".equals(length)) {
            out.add("Odpowiedź jest za długa (" + words + " słów, ok. " + String.format(Locale.ROOT, "%.1f", minutes)
                    .replace('.', ',') + " min) — skróć kontekst, zostaw działania i wynik.");
        }
        if ("PLURAL".equals(person.verdict())) {
            out.add("Mówisz głównie „my” (" + person.plural() + " razy, „ja” — " + person.singular()
                    + ") — rekruter chce wiedzieć, co było Twoim wkładem. Mów o swoich decyzjach i działaniach.");
        } else if ("NONE".equals(person.verdict())) {
            out.add("Nie widać, kto działał — opowiadaj w pierwszej osobie, o swoich decyzjach.");
        }
        if (fillerTotal > allowed) {
            out.add("Słowa-wypełniacze: " + String.join(", ", fillers.stream().limit(4)
                    .map(f -> "„" + f.word() + "” ×" + f.count()).toList()) + " — w rozmowie brzmią niepewnie.");
        }
        if (offerSkills != null && !offerSkills.isEmpty()) {
            if (!mentioned.isEmpty()) {
                out.add("Nawiązujesz do wymagań oferty: " + String.join(", ", mentioned) + ".");
            } else if (!missing.isEmpty()) {
                out.add("Odpowiedź nie dotyka wymagań oferty — jeśli to pasuje do pytania, wpleć np. "
                        + String.join(", ", missing.stream().limit(3).toList()) + ".");
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    private static String partFeedback(StarAssessment.StarPart p, String missing, String weak) {
        if (p.confidence() == 0) {
            return missing;
        }
        return p.confidence() < 1 ? weak : null;
    }

    private static List<String> words(String folded) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(folded);
        while (m.find()) {
            out.add(m.group().replace('’', '\''));
        }
        return out;
    }

    private static Filler filler(String label, String regex) {
        return new Filler(label, Pattern.compile("(?<!\\p{L})" + regex + "(?!\\p{L})"));
    }

    private static List<Marker> markers(double w1, List<String> l1, double w2, List<String> l2, double w3,
                                        List<String> l3) {
        List<Marker> out = new ArrayList<>();
        l1.forEach(p -> out.add(new Marker(Pattern.compile("(?<!\\p{L})" + p), w1)));
        l2.forEach(p -> out.add(new Marker(Pattern.compile("(?<!\\p{L})" + p), w2)));
        l3.forEach(p -> out.add(new Marker(Pattern.compile("(?<!\\p{L})" + p), w3)));
        return out;
    }
}

package pl.radarpracy.interview;

import org.junit.jupiter.api.Test;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StarAnalyzerTest {

    private final StarAnalyzer analyzer = new StarAnalyzer(new SkillDictionary());

    static final String GOOD = """
            W poprzedniej pracy w firmie z branży płatności mieliśmy problem: przetwarzanie płatności trwało \
            za długo i klienci zgłaszali opóźnienia, szczególnie w godzinach szczytu, kiedy ruch rósł kilkukrotnie. \
            Moim zadaniem było skrócenie czasu przetwarzania bez przepisywania całego systemu i bez przestojów. \
            Najpierw przeanalizowałam logi i metryki z ostatniego miesiąca i znalazłam wąskie gardło w synchronicznych \
            wywołaniach między usługami. Następnie zaproponowałam migrację komunikacji na Apache Kafka, przygotowałam \
            prototyp i przekonałam zespół na krótkim warsztacie. Wdrożyłam zmianę etapami, z flagami funkcji, \
            i napisałam testy integracyjne w JUnit oraz alerty w Grafanie. \
            W efekcie czas przetwarzania płatności spadł o 40%, a liczba zgłoszeń klientów zmniejszyła się o połowę \
            w ciągu dwóch miesięcy. Nauczyłam się też, że warto zaczynać od pomiarów, zanim zaproponuje się rozwiązanie.
            """;

    @Test
    void fullStarAnswerIsDetectedWithVerbatimEvidence() {
        StarAssessment a = analyzer.analyze(GOOD, List.of("Kafka", "Kubernetes", "JUnit"), List.of("Kafka", "Kubernetes"));
        StarAssessment.Star star = a.star();
        assertThat(star.situation().detected()).isTrue();
        assertThat(star.task().detected()).isTrue();
        assertThat(star.action().detected()).isTrue();
        assertThat(star.result().detected()).isTrue();
        for (StarAssessment.StarPart p : List.of(star.situation(), star.task(), star.action(), star.result())) {
            assertThat(GOOD).contains(p.evidence());
            assertThat(p.markers()).isNotEmpty();
        }
        assertThat(star.task().evidence()).startsWith("Moim zadaniem było");
        assertThat(star.result().evidence()).contains("40%");
        assertThat(a.hasNumbers()).isTrue();
        assertThat(a.numbers()).contains("40%");
        assertThat(a.length()).isEqualTo("OK");
        assertThat(a.firstPersonSingularVsPlural().verdict()).isEqualTo("SINGULAR");
        assertThat(a.score()).isGreaterThanOrEqualTo(90);
        assertThat(a.offerSkillsMentioned()).contains("Kafka", "JUnit");
        assertThat(a.offerSkillsMissing()).containsExactly("Kubernetes");
        assertThat(a.feedback()).anyMatch(f -> f.contains("Pełna struktura STAR"));
        assertThat(a.scoreBreakdown()).extracting(StarAssessment.ScorePart::max).contains(15.0, 25.0, 20.0, 10.0, 5.0);
        assertThat(a.source()).isEqualTo("offline");
    }

    @Test
    void pluralShortAnswerWithoutResultIsPenalised() {
        String answer = "No więc generalnie w zespole zrobiliśmy nowy moduł i wdrożyliśmy go, w sumie było ok, "
                + "generalnie wszyscy pracowaliśmy razem, w sumie jakby nasz zespół był dobry.";
        StarAssessment a = analyzer.analyze(answer, List.of(), List.of());
        assertThat(a.length()).isEqualTo("TOO_SHORT");
        assertThat(a.firstPersonSingularVsPlural().verdict()).isEqualTo("PLURAL");
        assertThat(a.star().result().detected()).isFalse();
        assertThat(a.hasNumbers()).isFalse();
        assertThat(a.fillerTotal()).isGreaterThanOrEqualTo(5);
        assertThat(a.fillerWords()).anyMatch(f -> f.word().equals("generalnie") && f.count() == 2);
        assertThat(a.score()).isLessThan(40);
        assertThat(a.feedback()).anyMatch(f -> f.contains("„my”"))
                .anyMatch(f -> f.contains("za krótka"))
                .anyMatch(f -> f.contains("Brakuje rezultatu"))
                .anyMatch(f -> f.contains("wypełniacze"));
    }

    @Test
    void englishAnswerIsRecognised() {
        String answer = """
                In my previous job our checkout service had frequent outages during sales. My task was to make it \
                stable before Black Friday. First, I analyzed the incident reports and found that the database pool \
                was exhausted. Then I introduced connection limits, added caching and wrote load tests. As a result, \
                we had zero outages during the sale and response time improved by 35%. I learned to always load test \
                before peak traffic, and I shared the checklist with two other teams so they could reuse it.""";
        StarAssessment a = analyzer.analyze(answer, List.of(), List.of());
        assertThat(a.star().situation().detected()).isTrue();
        assertThat(a.star().task().detected()).isTrue();
        assertThat(a.star().action().detected()).isTrue();
        assertThat(a.star().result().detected()).isTrue();
        assertThat(a.firstPersonSingularVsPlural().verdict()).isEqualTo("SINGULAR");
        assertThat(a.numbers()).contains("35%");
    }

    @Test
    void markersAreDeduplicated() {
        assertThat(StarAnalyzer.distinctMarkers(List.of("Moim zadaniem", "zadani", "przeanaliz", "przeanalizowałam",
                "Najpierw", "Najpierw"))).containsExactly("Moim zadaniem", "przeanalizowałam", "Najpierw");
    }

    @Test
    void yearsAreNotTreatedAsMetrics() {
        assertThat(StarAnalyzer.numbers("Pracowałam tam w 2022 roku.")).isEmpty();
        assertThat(StarAnalyzer.numbers("Skróciłam czas o 3 dni i dwukrotnie zwiększyłam pokrycie."))
                .contains("3 dni", "dwukrotnie");
    }

    @Test
    void sentencesAreVerbatimSubstrings() {
        String text = "Wersja 2.5 działała.  Potem? Tak!\nNowa linia bez kropki";
        var sentences = StarAnalyzer.sentences(text);
        assertThat(sentences).hasSize(4);
        sentences.forEach(s -> assertThat(text.substring(s.start(), s.start() + s.text().length())).isEqualTo(s.text()));
        assertThat(StarAnalyzer.wordCount(text)).isEqualTo(10);
    }
}

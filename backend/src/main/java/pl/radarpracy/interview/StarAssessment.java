package pl.radarpracy.interview;

import java.util.List;

/**
 * Ocena odpowiedzi metodą STAR (pomysł #54). Wynik liczbowy zawsze z heurystyk w kodzie.
 *
 * @param score               0–100
 * @param star                wykrycie elementów STAR z cytatami-dowodami
 * @param hasNumbers          czy odpowiedź zawiera liczby / miary
 * @param numbers             dosłowne fragmenty z liczbami
 * @param wordCount           liczba słów
 * @param length              TOO_SHORT, OK, TOO_LONG
 * @param speakingMinutes     szacowany czas wypowiedzi (130 słów na minutę)
 * @param firstPersonSingularVsPlural użycie „ja” vs „my”
 * @param fillerWords         słowa-wypełniacze z liczbą wystąpień
 * @param fillerTotal         łączna liczba wypełniaczy
 * @param offerSkillsMentioned umiejętności z wymagań oferty, o których mówi odpowiedź
 * @param offerSkillsMissing  umiejętności z wymagań obowiązkowych oferty, których odpowiedź nie dotyka
 * @param feedback            wskazówki po polsku
 * @param scoreBreakdown      jak policzono wynik (punkty za każdy element)
 * @param aiComment           dodatkowy komentarz modelu AI (null w trybie offline)
 * @param source              „ai”, gdy dołączono komentarz AI; „offline” w przeciwnym razie
 * @param savedId             id zapisanej próby (null, gdy nie zapisano)
 */
public record StarAssessment(int score, Star star, boolean hasNumbers, List<String> numbers, int wordCount,
                             String length, double speakingMinutes, PersonUsage firstPersonSingularVsPlural,
                             List<FillerCount> fillerWords, int fillerTotal, List<String> offerSkillsMentioned,
                             List<String> offerSkillsMissing, List<String> feedback, List<ScorePart> scoreBreakdown,
                             String aiComment, String source, Long savedId) {

    /** Cztery elementy STAR. */
    public record Star(StarPart situation, StarPart task, StarPart action, StarPart result) {
    }

    /**
     * Jeden element STAR.
     *
     * @param detected   czy wykryty (pewność ≥ 0,6)
     * @param confidence 0 (brak), 0,6 (słaby sygnał), 1 (wyraźny)
     * @param evidence   dosłowne zdanie z odpowiedzi, w którym wykryto element (null, gdy brak)
     * @param markers    znalezione wskaźniki (np. „w efekcie”, „wdrożyłam”)
     */
    public record StarPart(boolean detected, double confidence, String evidence, List<String> markers) {
    }

    /**
     * Pierwsza osoba: liczba pojedyncza („ja zrobiłam”) vs mnoga („my zrobiliśmy”).
     *
     * @param singular liczba wskaźników „ja”
     * @param plural   liczba wskaźników „my”
     * @param verdict  SINGULAR, PLURAL, BALANCED, NONE
     */
    public record PersonUsage(int singular, int plural, String verdict) {
    }

    /** Słowo-wypełniacz i liczba wystąpień. */
    public record FillerCount(String word, int count) {
    }

    /** Składnik wyniku. */
    public record ScorePart(String label, double points, double max) {
    }

    StarAssessment withAi(String comment, Long id) {
        return new StarAssessment(score, star, hasNumbers, numbers, wordCount, length, speakingMinutes,
                firstPersonSingularVsPlural, fillerWords, fillerTotal, offerSkillsMentioned, offerSkillsMissing,
                feedback, scoreBreakdown, comment, comment != null ? "ai" : "offline", id);
    }
}

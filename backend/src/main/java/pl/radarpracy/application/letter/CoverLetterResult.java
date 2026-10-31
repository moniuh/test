package pl.radarpracy.application.letter;

import java.util.List;

/**
 * Wygenerowany list motywacyjny (pomysł #42).
 *
 * @param text     treść listu do edycji przez kandydata
 * @param source   „ai” albo „offline” (szablon z cytatów CV)
 * @param evidence dowody: wymaganie z oferty i dosłowny cytat z CV, który je potwierdza
 * @param notice   dodatkowa informacja dla kandydata (np. dlaczego użyto trybu offline) lub null
 */
public record CoverLetterResult(String text, String source, List<Evidence> evidence, Long cvId, String cvName,
                                String tone, String language, String notice) {

    /**
     * Dowód z CV.
     *
     * @param requirement wymaganie z oferty
     * @param quote       dosłowny fragment CV (sprawdzony w kodzie)
     * @param mustHave    czy wymaganie jest obowiązkowe
     * @param partial     czy wymaganie jest spełnione tylko częściowo (np. mniej lat niż wymagane)
     */
    public record Evidence(String requirement, String quote, boolean mustHave, boolean partial) {
    }
}

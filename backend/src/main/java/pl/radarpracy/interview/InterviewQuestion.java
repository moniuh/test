package pl.radarpracy.interview;

/**
 * Pytanie, które może paść na rozmowie (pomysł #53).
 *
 * @param question    treść pytania
 * @param category    techniczne, behawioralne, motywacyjne, luka, o-firmę
 * @param skill       umiejętność ze słownika, której dotyczy (może być null)
 * @param why         z którego wymagania oferty wynika pytanie
 * @param tip         jak się przygotować
 * @param requirement dosłowna treść wymagania z oferty (może być null)
 * @param evidence    dosłowny cytat z CV, na którym można oprzeć odpowiedź (może być null)
 */
public record InterviewQuestion(String question, String category, String skill, String why, String tip,
                                String requirement, String evidence) {

    public static final String TECHNICAL = "techniczne";
    public static final String BEHAVIORAL = "behawioralne";
    public static final String MOTIVATIONAL = "motywacyjne";
    public static final String GAP = "luka";
    public static final String COMPANY = "o-firmę";
}

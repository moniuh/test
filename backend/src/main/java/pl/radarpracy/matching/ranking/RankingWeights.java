package pl.radarpracy.matching.ranking;

/**
 * Wszystkie wagi i progi rankingu ofert w jednym miejscu (pomysły #31, #35).
 *
 * <p>Ranking jest liczony deterministycznie w kodzie: każdy składnik bazowy ma wartość 0–1,
 * a jego wkład to {@code wartość × waga × 100} pkt. Wagi składników bazowych sumują się do 1,0,
 * więc ranking bez korekty mieści się w 0–100. Korekta z uczenia preferencji ({@link Factor#LEARNED})
 * ma wartość ze znakiem (−1…+1) i dodaje najwyżej ±{@link #LEARNING_MAX_POINTS} pkt; wynik końcowy
 * jest obcinany do 0–100.</p>
 */
public final class RankingWeights {

    private RankingWeights() {
    }

    /** Składniki rankingu z nazwą dla kandydata i wagą. */
    public enum Factor {
        /** Dopasowanie wymagań oferty do CV — największa waga. */
        CV_MATCH("cv", "Dopasowanie CV", 0.50),
        /** Wynagrodzenie (równoważnik UoP brutto) względem minimum z profilu. */
        SALARY("salary", "Wynagrodzenie", 0.15),
        /** Tryb pracy względem akceptowanych trybów. */
        WORK_MODE("workMode", "Tryb pracy", 0.10),
        /** Lokalizacja względem preferowanych miejsc (praca zdalna = bez znaczenia). */
        LOCATION("location", "Lokalizacja", 0.10),
        /** Świeżość ogłoszenia (data publikacji, wygaśnięcie). */
        FRESHNESS("freshness", "Świeżość ogłoszenia", 0.08),
        /** Forma umowy względem akceptowanych form. */
        CONTRACT("contract", "Forma umowy", 0.07),
        /** Korekta z ocen kandydata (zapisane i odrzucone oferty) — wartość ze znakiem. */
        LEARNED("learned", "Twoje oceny (uczenie)", 0.10);

        private final String key;
        private final String label;
        private final double weight;

        Factor(String key, String label, double weight) {
            this.key = key;
            this.label = label;
            this.weight = weight;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }

        public double weight() {
            return weight;
        }

        /** Składnik bazowy (wartość 0–1); korekta z uczenia nim nie jest. */
        public boolean base() {
            return this != LEARNED;
        }
    }

    /** Wartość składnika, gdy brakuje danych (oferta nie podaje, profil nie określa) — „neutralnie”. */
    public static final double NEUTRAL = 0.5;

    // --- świeżość ---

    /** Ogłoszenie opublikowane najwyżej tyle dni temu ma pełną wartość składnika świeżości. */
    public static final int FRESH_DAYS = 3;
    /** Po tylu dniach składnik świeżości spada liniowo do zera. */
    public static final int STALE_DAYS = 45;

    // --- wynagrodzenie: krzywa odcinkowo liniowa względem ilorazu środek widełek / minimum ---

    /** Iloraz, przy którym składnik wynagrodzenia ma wartość 0. */
    public static final double SALARY_ZERO_RATIO = 0.8;
    /** Wartość składnika, gdy środek widełek równa się minimum. */
    public static final double SALARY_AT_MIN_VALUE = 0.6;
    /** Iloraz, od którego składnik wynagrodzenia ma pełną wartość. */
    public static final double SALARY_FULL_RATIO = 1.2;

    // --- uczenie preferencji (#35) ---

    /** Minimalna liczba ocen (zapisane + odrzucone), zanim uczenie wpłynie na ranking. */
    public static final int LEARNING_MIN_RATINGS = 3;
    /** Wygładzanie Laplace'a w log-odds cech. */
    public static final double LEARNING_SMOOTHING = 1.0;
    /** Ile punktów rankingu daje jedna jednostka log-odds cechy. */
    public static final double LEARNING_POINTS_PER_LOG_ODDS = 4.0;
    /** Limit wkładu umiejętności (łącznie) w punktach. */
    public static final double LEARNING_CAP_SKILLS = 6.0;
    /** Limit wkładu firmy w punktach. */
    public static final double LEARNING_CAP_COMPANY = 4.0;
    /** Limit wkładu trybu pracy, formy umowy i poziomu stanowiska (każdego osobno) w punktach. */
    public static final double LEARNING_CAP_OTHER = 3.0;
    /** Łączny limit korekty z uczenia w punktach (±). */
    public static final double LEARNING_MAX_POINTS = Factor.LEARNED.weight() * 100;
}

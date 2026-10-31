package pl.radarpracy.skills;

import java.util.List;

/**
 * Umiejętność ze słownika.
 *
 * @param name     nazwa kanoniczna (np. „PostgreSQL”)
 * @param category kategoria (np. „baza-danych”, „jezyk-obcy”)
 * @param family   rodzina pokrewnych umiejętności (np. „sql-db”) — daje częściowe dopasowanie; może być null
 * @param aliases  warianty zapisu w postaci złożonej ({@link pl.radarpracy.common.Text#fold})
 * @param implies  umiejętności, które ta umiejętność obejmuje (np. Spring Boot ⇒ Spring, Java)
 * @param prefix   czy aliasy dopasowywać jako prefiks słowa (polska fleksja: „angielsk” → „angielskiego”)
 */
public record Skill(String name, String category, String family, List<String> aliases, List<String> implies,
                   boolean prefix) {

    public boolean isLanguage() {
        return "jezyk-obcy".equals(category);
    }
}

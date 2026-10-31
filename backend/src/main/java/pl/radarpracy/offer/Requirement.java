package pl.radarpracy.offer;

/**
 * Wymaganie z oferty.
 *
 * @param text     treść wymagania (dosłownie z ogłoszenia lub wpisana przez użytkownika)
 * @param mustHave true = obowiązkowe, false = mile widziane
 * @param minYears minimalna liczba lat doświadczenia, jeśli podana
 */
public record Requirement(String text, boolean mustHave, Integer minYears) {

    public static Requirement must(String text) {
        return new Requirement(text, true, null);
    }

    public static Requirement nice(String text) {
        return new Requirement(text, false, null);
    }
}

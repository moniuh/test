package pl.radarpracy.profile;

/**
 * Umiejętność zadeklarowana w profilu.
 *
 * @param name  nazwa (kanoniczna, jeśli rozpoznana przez słownik)
 * @param level poziom 1–5 (opcjonalnie)
 * @param years lata doświadczenia (opcjonalnie)
 */
public record SkillEntry(String name, Integer level, Integer years) {
}

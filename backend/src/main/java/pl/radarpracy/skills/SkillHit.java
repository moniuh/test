package pl.radarpracy.skills;

/**
 * Wystąpienie umiejętności w tekście. Indeksy odnoszą się do oryginalnego tekstu.
 *
 * @param skill nazwa kanoniczna
 * @param start początek dopasowania (włącznie)
 * @param end   koniec dopasowania (wyłącznie)
 * @param alias dopasowany alias
 */
public record SkillHit(String skill, int start, int end, String alias) {
}

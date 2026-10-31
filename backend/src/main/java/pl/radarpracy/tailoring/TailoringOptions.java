package pl.radarpracy.tailoring;

/**
 * Opcje generatora dopasowanego CV.
 *
 * @param hideIrrelevantBullets zwiń punkty doświadczenia niezwiązane z żadnym wymaganiem (zawsze zostaje
 *                              co najmniej jeden punkt na stanowisko; stanowiska i daty nigdy nie znikają)
 * @param maxBulletsPerRole     najwyżej tyle punktów na stanowisko (najtrafniejsze); null/0 = bez limitu
 * @param useAi                 przeredaguj podsumowanie i punkty modelem AI (wynik zawsze przechodzi strażnika)
 */
public record TailoringOptions(Boolean hideIrrelevantBullets, Integer maxBulletsPerRole, Boolean useAi) {

    public static TailoringOptions defaults() {
        return new TailoringOptions(false, null, false);
    }

    public boolean hide() {
        return Boolean.TRUE.equals(hideIrrelevantBullets);
    }

    public boolean ai() {
        return Boolean.TRUE.equals(useAi);
    }

    /** Limit punktów (0 = bez limitu). */
    public int limit() {
        return maxBulletsPerRole == null || maxBulletsPerRole < 0 ? 0 : Math.min(maxBulletsPerRole, 50);
    }

    public static TailoringOptions orDefaults(TailoringOptions o) {
        return o == null ? defaults() : o;
    }
}

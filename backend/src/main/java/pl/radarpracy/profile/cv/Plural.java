package pl.radarpracy.profile.cv;

/** Polska odmiana rzeczowników po liczebnikach: 1 rok, 2 lata, 5 lat, 22 lata. */
final class Plural {

    private Plural() {
    }

    static String of(int n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        int mod10 = Math.abs(n) % 10;
        int mod100 = Math.abs(n) % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }

    /** „3 lata”, „5 lat”, „1 rok”. */
    static String years(int n) {
        return n + " " + of(n, "rok", "lata", "lat");
    }
}

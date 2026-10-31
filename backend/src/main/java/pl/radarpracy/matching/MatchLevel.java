package pl.radarpracy.matching;

/** Stopień spełnienia wymagania. */
public enum MatchLevel {
    /** spełnione, z dowodem w CV */
    FULL,
    /** spełnione częściowo (np. za mało lat, niższy poziom języka) */
    PARTIAL,
    /** brak, ale kandydat zna pokrewną technologię z tej samej rodziny */
    RELATED,
    /** jest w profilu kandydata, ale nie w wybranej wersji CV */
    PROFILE_ONLY,
    /** brak */
    MISSING
}

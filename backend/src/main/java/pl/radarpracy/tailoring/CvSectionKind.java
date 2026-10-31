package pl.radarpracy.tailoring;

/** Rodzaj sekcji CV rozpoznany po nagłówku (PL/EN). */
public enum CvSectionKind {
    /** dane osobowe i kontakt — wszystko przed pierwszym rozpoznanym nagłówkiem */
    HEADER,
    SUMMARY,
    /** „Kluczowe kompetencje pod to stanowisko” — sekcja dodawana przez tailoring */
    KEY_COMPETENCES,
    EXPERIENCE,
    SKILLS,
    LANGUAGES,
    EDUCATION,
    PROJECTS,
    CERTIFICATES,
    /** inne rozpoznane nagłówki (zainteresowania, osiągnięcia…) — przepisywane bez zmian */
    OTHER
}

package pl.radarpracy.application;

/** Etap aplikacji (kolumny tablicy Kanban). */
public enum ApplicationStatus {
    /** do wysłania */
    PLANNED,
    /** wysłana */
    APPLIED,
    /** pierwszy kontakt / rozmowa wstępna */
    SCREENING,
    /** rozmowy rekrutacyjne */
    INTERVIEW,
    /** otrzymana oferta */
    OFFER,
    /** oferta przyjęta */
    ACCEPTED,
    /** odrzucona przez firmę */
    REJECTED,
    /** wycofana przez kandydata */
    WITHDRAWN,
    /** brak odpowiedzi (ghosting) */
    NO_RESPONSE;

    /** Czy status oznacza, że firma odpowiedziała. */
    public boolean isResponse() {
        return this == SCREENING || this == INTERVIEW || this == OFFER || this == ACCEPTED || this == REJECTED;
    }

    /** Czy aplikacja jest zamknięta. */
    public boolean isClosed() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN || this == NO_RESPONSE;
    }
}

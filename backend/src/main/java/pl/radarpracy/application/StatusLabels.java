package pl.radarpracy.application;

import java.util.EnumMap;
import java.util.Map;

/** Polskie etykiety statusów, zdarzeń i rodzajów rozmów (zgodne z frontendem {@code api/labels.ts}). */
public final class StatusLabels {

    private static final Map<ApplicationStatus, String> STATUS = new EnumMap<>(ApplicationStatus.class);
    private static final Map<EventType, String> EVENT = new EnumMap<>(EventType.class);
    private static final Map<InterviewKind, String> KIND = new EnumMap<>(InterviewKind.class);

    static {
        STATUS.put(ApplicationStatus.PLANNED, "Do wysłania");
        STATUS.put(ApplicationStatus.APPLIED, "Wysłana");
        STATUS.put(ApplicationStatus.SCREENING, "Pierwszy kontakt");
        STATUS.put(ApplicationStatus.INTERVIEW, "Rozmowy");
        STATUS.put(ApplicationStatus.OFFER, "Oferta");
        STATUS.put(ApplicationStatus.ACCEPTED, "Przyjęta");
        STATUS.put(ApplicationStatus.REJECTED, "Odrzucona");
        STATUS.put(ApplicationStatus.WITHDRAWN, "Wycofana");
        STATUS.put(ApplicationStatus.NO_RESPONSE, "Brak odpowiedzi");
        EVENT.put(EventType.CREATED, "Utworzono");
        EVENT.put(EventType.STATUS_CHANGED, "Zmiana statusu");
        EVENT.put(EventType.NOTE, "Notatka");
        EVENT.put(EventType.FOLLOW_UP_SENT, "Wysłano follow-up");
        EVENT.put(EventType.MESSAGE, "Wiadomość");
        EVENT.put(EventType.INTERVIEW_SCHEDULED, "Zaplanowano rozmowę");
        EVENT.put(EventType.INTERVIEW_DONE, "Rozmowa odbyta");
        KIND.put(InterviewKind.PHONE, "Telefoniczna");
        KIND.put(InterviewKind.VIDEO, "Wideo");
        KIND.put(InterviewKind.ONSITE, "W biurze");
        KIND.put(InterviewKind.TECHNICAL, "Techniczna");
        KIND.put(InterviewKind.HR, "HR");
        KIND.put(InterviewKind.FINAL, "Finałowa");
    }

    private StatusLabels() {
    }

    public static String status(ApplicationStatus s) {
        return s == null ? "—" : STATUS.get(s);
    }

    public static String event(EventType t) {
        return t == null ? "—" : EVENT.get(t);
    }

    public static String kind(InterviewKind k) {
        return k == null ? "Rozmowa" : KIND.get(k);
    }
}

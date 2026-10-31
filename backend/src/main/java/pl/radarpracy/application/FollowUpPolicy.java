package pl.radarpracy.application;

import java.time.LocalDate;

/**
 * Reguły terminów follow-upu (pomysł #41) — czysta logika, bez dostępu do bazy.
 *
 * <ul>
 *   <li>po wysłaniu aplikacji: termin = data wysłania + liczba dni z profilu,</li>
 *   <li>odpowiedź firmy lub zamknięcie aplikacji: termin znika,</li>
 *   <li>po wysłaniu follow-upu: kolejny termin = dziś + liczba dni z profilu,</li>
 *   <li>odłożenie: od późniejszej z dat (dziś, obecny termin) o podaną liczbę dni.</li>
 * </ul>
 */
public final class FollowUpPolicy {

    /** Domyślna liczba dni, gdy profil jej nie podaje. */
    public static final int DEFAULT_DAYS = 7;
    /** Najdłuższe dopuszczalne odłożenie przypomnienia. */
    public static final int MAX_SNOOZE_DAYS = 90;

    private FollowUpPolicy() {
    }

    /** Liczba dni z profilu albo wartość domyślna (gdy brak lub niedodatnia). */
    public static int days(Integer profileDays) {
        return profileDays == null || profileDays < 1 ? DEFAULT_DAYS : profileDays;
    }

    /** Pierwszy termin po wysłaniu aplikacji. */
    public static LocalDate afterApplied(LocalDate appliedAt, LocalDate today, int days) {
        return (appliedAt != null ? appliedAt : today).plusDays(days);
    }

    /** Czy przejście na ten status usuwa zaplanowany follow-up (firma odpowiedziała albo koniec procesu). */
    public static boolean clears(ApplicationStatus status) {
        return status.isResponse() || status.isClosed();
    }

    /** Czy przejście na ten status planuje pierwszy follow-up. */
    public static boolean schedules(ApplicationStatus status) {
        return status == ApplicationStatus.APPLIED;
    }

    /** Nowy termin po odłożeniu o {@code days} dni. */
    public static LocalDate snooze(LocalDate current, LocalDate today, int days) {
        if (days < 1 || days > MAX_SNOOZE_DAYS) {
            throw new IllegalArgumentException("Przypomnienie można odłożyć o 1–" + MAX_SNOOZE_DAYS + " dni");
        }
        LocalDate base = current == null || current.isBefore(today) ? today : current;
        return base.plusDays(days);
    }
}

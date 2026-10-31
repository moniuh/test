package pl.radarpracy.analytics;

import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.offer.JobOffer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fakty o jednej aplikacji wyliczone deterministycznie z encji, historii zdarzeń i rozmów —
 * wspólna podstawa lejka (#71), skuteczności (#72), pulpitu (#73), „Co dalej?” (#97) i raportu (#79).
 *
 * <p>Etapy są monotoniczne: dalszy etap oznacza też wszystkie wcześniejsze (np. umówiona rozmowa
 * oznacza, że firma odpowiedziała), więc konwersje w lejku nigdy nie przekraczają 100%.</p>
 *
 * @param application  aplikacja (z ofertą)
 * @param events       zdarzenia aplikacji w kolejności chronologicznej
 * @param interviews   rozmowy w ramach aplikacji (w kolejności terminów)
 * @param sent         czy aplikacja została wysłana (PLANNED się nie liczy)
 * @param sentDate     data wysłania (appliedAt, a gdy brak — pierwsze zdarzenie „wysłania” lub data utworzenia)
 * @param stage        najdalszy osiągnięty etap
 * @param responseDate data pierwszej odpowiedzi firmy, jeśli znana
 * @param plannedSince od kiedy aplikacja czeka w statusie PLANNED (tylko dla PLANNED)
 */
public record AppFacts(JobApplication application, List<ApplicationEvent> events, List<Interview> interviews,
                       boolean sent, LocalDate sentDate, Stage stage, LocalDate responseDate, LocalDate plannedSince) {

    /** Etapy lejka rekrutacyjnego. */
    public enum Stage {
        NOT_SENT, SENT, RESPONSE, INTERVIEW, OFFER, ACCEPTED
    }

    /** Statusy, które oznaczają, że aplikacja wyszła do firmy. */
    private static final Set<ApplicationStatus> SENT_STATUSES = EnumSet.of(ApplicationStatus.APPLIED,
            ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER,
            ApplicationStatus.ACCEPTED, ApplicationStatus.REJECTED, ApplicationStatus.NO_RESPONSE);

    public static AppFacts of(JobApplication a, List<ApplicationEvent> events, List<Interview> interviews,
                              ZoneId zone) {
        List<ApplicationEvent> ev = new ArrayList<>(events == null ? List.of() : events);
        ev.sort(Comparator.comparing(ApplicationEvent::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Interview> iv = interviews == null ? List.of() : List.copyOf(interviews);

        Set<ApplicationStatus> seen = EnumSet.noneOf(ApplicationStatus.class);
        if (a.getStatus() != null) {
            seen.add(a.getStatus());
        }
        for (ApplicationEvent e : ev) {
            if (e.getToStatus() != null) {
                seen.add(e.getToStatus());
            }
            if (e.getFromStatus() != null) {
                seen.add(e.getFromStatus());
            }
        }

        boolean sent = isSent(a, seen);
        Stage stage = Stage.NOT_SENT;
        if (sent) {
            boolean accepted = seen.contains(ApplicationStatus.ACCEPTED);
            boolean offered = accepted || seen.contains(ApplicationStatus.OFFER);
            boolean interviewed = offered || seen.contains(ApplicationStatus.INTERVIEW) || !iv.isEmpty()
                    || ev.stream().anyMatch(e -> e.getType() == EventType.INTERVIEW_SCHEDULED
                    || e.getType() == EventType.INTERVIEW_DONE);
            boolean responded = interviewed || a.getFirstResponseAt() != null
                    || seen.stream().anyMatch(ApplicationStatus::isResponse);
            stage = accepted ? Stage.ACCEPTED : offered ? Stage.OFFER : interviewed ? Stage.INTERVIEW
                    : responded ? Stage.RESPONSE : Stage.SENT;
        }

        LocalDate sentDate = sent ? sentDate(a, ev, zone) : null;
        LocalDate responseDate = stage.ordinal() >= Stage.RESPONSE.ordinal() ? responseDate(a, ev, zone) : null;
        LocalDate plannedSince = a.getStatus() == ApplicationStatus.PLANNED ? plannedSince(a, ev, zone) : null;
        return new AppFacts(a, List.copyOf(ev), iv, sent, sentDate, stage, responseDate, plannedSince);
    }

    /**
     * PLANNED nigdy nie jest wysłana. WITHDRAWN bez daty wysłania liczymy tylko wtedy, gdy historia
     * pokazuje, że aplikacja była w statusie „wysłanym” (wycofanie przed wysłaniem to nie aplikacja).
     */
    private static boolean isSent(JobApplication a, Set<ApplicationStatus> seen) {
        if (a.getStatus() == ApplicationStatus.PLANNED) {
            return false;
        }
        if (a.getAppliedAt() != null) {
            return true;
        }
        if (a.getStatus() != ApplicationStatus.WITHDRAWN) {
            return true;
        }
        return seen.stream().anyMatch(SENT_STATUSES::contains);
    }

    private static LocalDate sentDate(JobApplication a, List<ApplicationEvent> ev, ZoneId zone) {
        if (a.getAppliedAt() != null) {
            return a.getAppliedAt();
        }
        for (ApplicationEvent e : ev) {
            if (e.getToStatus() != null && SENT_STATUSES.contains(e.getToStatus()) && e.getOccurredAt() != null) {
                return toDate(e.getOccurredAt(), zone);
            }
        }
        return a.getCreatedAt() == null ? null : toDate(a.getCreatedAt(), zone);
    }

    private static LocalDate responseDate(JobApplication a, List<ApplicationEvent> ev, ZoneId zone) {
        if (a.getFirstResponseAt() != null) {
            return a.getFirstResponseAt();
        }
        for (ApplicationEvent e : ev) {
            boolean response = (e.getToStatus() != null && e.getToStatus().isResponse())
                    || e.getType() == EventType.INTERVIEW_SCHEDULED;
            if (response && e.getOccurredAt() != null) {
                return toDate(e.getOccurredAt(), zone);
            }
        }
        return null;
    }

    private static LocalDate plannedSince(JobApplication a, List<ApplicationEvent> ev, ZoneId zone) {
        for (ApplicationEvent e : ev) {
            if (e.getType() == EventType.CREATED && e.getOccurredAt() != null) {
                return toDate(e.getOccurredAt(), zone);
            }
        }
        return a.getCreatedAt() == null ? null : toDate(a.getCreatedAt(), zone);
    }

    static LocalDate toDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }

    /** Czy osiągnięto co najmniej podany etap. */
    public boolean reached(Stage s) {
        return stage.ordinal() >= s.ordinal();
    }

    /** Liczba dni od wysłania do pierwszej odpowiedzi (null, gdy nieznana lub niespójna). */
    public Long daysToResponse() {
        if (sentDate == null || responseDate == null || responseDate.isBefore(sentDate)) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(sentDate, responseDate);
    }

    public Long id() {
        return application.getId();
    }

    public ApplicationStatus status() {
        return application.getStatus();
    }

    public JobOffer offer() {
        return application.getOffer();
    }

    public Long offerId() {
        return offer() == null ? null : offer().getId();
    }

    public String company() {
        JobOffer o = offer();
        return o == null || o.getCompany() == null || o.getCompany().isBlank() ? "(firma nieznana)" : o.getCompany();
    }

    public String position() {
        JobOffer o = offer();
        return o == null ? "" : Objects.toString(o.getTitle(), "");
    }

    /**
     * Nazwa firmy do porównań: bez wielkości liter, polskich znaków, form prawnych i interpunkcji
     * („LogiTech Solutions sp. z o.o.” = „LogiTech Solutions”).
     */
    public static String normalizeCompany(String company) {
        if (company == null) {
            return "";
        }
        String f = " " + pl.radarpracy.common.Text.fold(company).replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        f = f.replaceAll(" (sp z o o|sp k|sp j|spolka z ograniczona odpowiedzialnoscia|spolka akcyjna|s a|sa|ltd|gmbh|"
                + "ab|inc|llc|plc|bv|oy|as) ", " ");
        return f.replaceAll("\\s+", " ").strip();
    }

    /** Kanał aplikacji: źródło aplikacji, a gdy brak — źródło oferty. */
    public String source() {
        String s = application.getSource();
        if (s == null || s.isBlank()) {
            s = offer() == null ? null : offer().getSource();
        }
        return s == null || s.isBlank() ? null : s.strip();
    }
}

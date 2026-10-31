package pl.radarpracy.application.followup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.FollowUpPolicy;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Przypomnienia o follow-upie (pomysł #41). */
@Service
public class FollowUpService {

    /** Domyślny horyzont listy „nadchodzące”. */
    public static final int DEFAULT_HORIZON = 14;

    private final ApplicationService applications;

    public FollowUpService(ApplicationService applications) {
        this.applications = applications;
    }

    public FollowUpList list(Integer horizonDays) {
        int horizon = horizonDays == null ? DEFAULT_HORIZON : Math.max(0, Math.min(horizonDays, 365));
        return list(applications.all(), LocalDate.now(), horizon, applications.followUpDays());
    }

    /** Podział na zaległe i nadchodzące — czysta logika (testowana jednostkowo). */
    static FollowUpList list(Collection<JobApplication> all, LocalDate today, int horizon, int followUpDays) {
        List<FollowUpItem> overdue = new ArrayList<>();
        List<FollowUpItem> upcoming = new ArrayList<>();
        for (JobApplication a : all) {
            if (a.getNextFollowUpAt() == null || a.getStatus().isClosed()) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, a.getNextFollowUpAt());
            if (days <= 0) {
                overdue.add(item(a, days));
            } else if (days <= horizon) {
                upcoming.add(item(a, days));
            }
        }
        Comparator<FollowUpItem> byDate = Comparator.comparing(FollowUpItem::nextFollowUpAt)
                .thenComparing(FollowUpItem::applicationId);
        overdue.sort(byDate);
        upcoming.sort(byDate);
        return new FollowUpList(today, followUpDays, horizon, overdue, upcoming);
    }

    private static FollowUpItem item(JobApplication a, long days) {
        return new FollowUpItem(a.getId(), a.getOffer().getId(), a.getOffer().getTitle(), a.getOffer().getCompany(),
                a.getStatus(), a.getAppliedAt(), a.getLastContactAt(), a.getNextFollowUpAt(), days, days <= 0,
                a.getContactName(), a.getContactEmail());
    }

    /**
     * Zapisuje wysłany follow-up: zdarzenie FOLLOW_UP_SENT, ostatni kontakt = dziś i kolejny termin
     * za tyle dni, ile podano w profilu (dla zamkniętych aplikacji bez kolejnego terminu).
     */
    @Transactional
    public JobApplication sent(Long id, String note) {
        JobApplication a = applications.get(id);
        LocalDate today = LocalDate.now();
        applications.addEvent(id, EventType.FOLLOW_UP_SENT,
                Text.isBlank(note) ? "Wysłano wiadomość z pytaniem o status rekrutacji" : note.strip());
        a.setLastContactAt(today);
        a.setNextFollowUpAt(a.getStatus().isClosed() ? null : today.plusDays(applications.followUpDays()));
        return applications.save(a);
    }

    /** Odkłada przypomnienie o {@code days} dni. */
    @Transactional
    public JobApplication snooze(Long id, Integer days) {
        if (days == null) {
            throw new BadRequestException("Podaj, o ile dni odłożyć przypomnienie");
        }
        JobApplication a = applications.get(id);
        if (a.getStatus().isClosed()) {
            throw new BadRequestException("Aplikacja jest zamknięta — nie ma czego przypominać");
        }
        try {
            a.setNextFollowUpAt(FollowUpPolicy.snooze(a.getNextFollowUpAt(), LocalDate.now(), days));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        return applications.save(a);
    }
}

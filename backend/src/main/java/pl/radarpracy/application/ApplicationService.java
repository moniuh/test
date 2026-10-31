package pl.radarpracy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Operacje na aplikacjach — punkt wejścia dla innych modułów. */
@Service
public class ApplicationService {

    private static final Comparator<JobApplication> BOARD_ORDER = Comparator
            .comparing((JobApplication a) -> a.getBoardPosition() == null ? 0 : a.getBoardPosition())
            .thenComparing(JobApplication::getId);

    private final JobApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final InterviewRepository interviews;
    private final OfferService offers;
    private final ProfileService profiles;

    public ApplicationService(JobApplicationRepository applications, ApplicationEventRepository events,
                              InterviewRepository interviews, OfferService offers, ProfileService profiles) {
        this.applications = applications;
        this.events = events;
        this.interviews = interviews;
        this.offers = offers;
        this.profiles = profiles;
    }

    public List<JobApplication> all() {
        return applications.findAllByOrderByStatusAscBoardPositionAscIdAsc();
    }

    public JobApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> NotFoundException.of("Aplikacja", id));
    }

    public List<JobApplication> forOffer(Long offerId) {
        return applications.findByOfferId(offerId);
    }

    public List<ApplicationEvent> events(Long applicationId) {
        get(applicationId);
        return events.findByApplicationIdOrderByOccurredAtAsc(applicationId);
    }

    /** Rozmowy w ramach aplikacji (rosnąco po terminie). */
    public List<Interview> interviews(Long applicationId) {
        return interviews.findByApplicationIdOrderByScheduledAtAsc(applicationId);
    }

    /** Liczba dni do follow-upu z profilu kandydata (pomysł #41). */
    public int followUpDays() {
        return FollowUpPolicy.days(profiles.profile().getFollowUpDays());
    }

    @Transactional
    public JobApplication create(ApplicationRequest req) {
        if (req.offerId() == null) {
            throw new BadRequestException("Wybierz ofertę");
        }
        JobOffer offer = offers.get(req.offerId());
        JobApplication a = new JobApplication();
        a.setOffer(offer);
        a.setStatus(req.status() == null ? ApplicationStatus.APPLIED : req.status());
        a.setAppliedAt(req.appliedAt() != null ? req.appliedAt()
                : a.getStatus() == ApplicationStatus.PLANNED ? null : LocalDate.now());
        a.setSource(req.source() != null ? req.source() : offer.getSource());
        CvVariant cv = profiles.cvOrActive(req.cvVariantId()).orElse(null);
        if (cv != null) {
            a.setCvVariantId(cv.getId());
            a.setCvVariantName(cv.getName());
        }
        a.setCoverLetter(req.coverLetter());
        a.setNextFollowUpAt(req.nextFollowUpAt());
        if (FollowUpPolicy.schedules(a.getStatus()) && a.getNextFollowUpAt() == null) {
            a.setNextFollowUpAt(FollowUpPolicy.afterApplied(a.getAppliedAt(), LocalDate.now(), followUpDays()));
        }
        a.setContactName(req.contactName());
        a.setContactEmail(req.contactEmail());
        a.setSalaryExpectation(req.salaryExpectation());
        a.setNotes(req.notes());
        a.setBoardPosition((int) applications.count());
        if (isSent(a.getStatus())) {
            takeSnapshot(a, cv);
        }
        JobApplication saved = applications.save(a);
        ApplicationEvent e = new ApplicationEvent(saved.getId(), EventType.CREATED, null);
        e.setToStatus(saved.getStatus());
        events.save(e);
        return saved;
    }

    @Transactional
    public JobApplication update(Long id, ApplicationRequest req) {
        JobApplication a = get(id);
        if (req.appliedAt() != null) {
            a.setAppliedAt(req.appliedAt());
        }
        if (req.source() != null) {
            a.setSource(req.source());
        }
        if (req.cvVariantId() != null) {
            CvVariant cv = profiles.cv(req.cvVariantId());
            a.setCvVariantId(cv.getId());
            a.setCvVariantName(cv.getName());
        }
        if (req.coverLetter() != null) {
            a.setCoverLetter(req.coverLetter());
        }
        a.setNextFollowUpAt(req.nextFollowUpAt());
        if (req.contactName() != null) {
            a.setContactName(req.contactName());
        }
        if (req.contactEmail() != null) {
            a.setContactEmail(req.contactEmail());
        }
        if (req.salaryExpectation() != null) {
            a.setSalaryExpectation(req.salaryExpectation());
        }
        if (req.notes() != null) {
            a.setNotes(req.notes());
        }
        // status na końcu: zmiana statusu może zaplanować lub wyczyścić follow-up i zapisać snapshot
        if (req.status() != null && req.status() != a.getStatus()) {
            changeStatus(a, req.status(), null);
        }
        return applications.save(a);
    }

    @Transactional
    public JobApplication changeStatus(Long id, ApplicationStatus status, String note) {
        JobApplication a = get(id);
        changeStatus(a, status, note);
        return applications.save(a);
    }

    /**
     * Zmiana statusu z wpisem na osi czasu, aktualizacją dat odpowiedzi, terminem follow-upu (#41)
     * i snapshotem wysłanych dokumentów przy wyjściu ze statusu „do wysłania” (#46).
     */
    void changeStatus(JobApplication a, ApplicationStatus status, String note) {
        if (status == null) {
            throw new BadRequestException("Podaj status");
        }
        ApplicationStatus from = a.getStatus();
        if (from == status) {
            return;
        }
        a.setStatus(status);
        LocalDate today = LocalDate.now();
        if (status == ApplicationStatus.APPLIED && a.getAppliedAt() == null) {
            a.setAppliedAt(today);
        }
        if (status.isResponse()) {
            if (a.getFirstResponseAt() == null) {
                a.setFirstResponseAt(today);
            }
            a.setLastContactAt(today);
        }
        if (FollowUpPolicy.clears(status)) {
            a.setNextFollowUpAt(null);
        } else if (FollowUpPolicy.schedules(status) && a.getNextFollowUpAt() == null) {
            a.setNextFollowUpAt(FollowUpPolicy.afterApplied(a.getAppliedAt(), today, followUpDays()));
        }
        if (from == ApplicationStatus.PLANNED && isSent(status) && !hasSnapshot(a)) {
            takeSnapshot(a, currentCv(a));
        }
        ApplicationEvent e = new ApplicationEvent(a.getId(), EventType.STATUS_CHANGED, note);
        e.setFromStatus(from);
        e.setToStatus(status);
        events.save(e);
    }

    /**
     * Przeniesienie karty na tablicy Kanban (pomysł #39): zmiana statusu (z wpisem na osi czasu)
     * i kolejności w kolumnie. Pozycje w kolumnie docelowej (i źródłowej) są numerowane od 0.
     */
    @Transactional
    public JobApplication move(Long id, ApplicationStatus status, Integer position, String note) {
        if (status == null) {
            throw new BadRequestException("Podaj kolumnę docelową");
        }
        JobApplication a = get(id);
        ApplicationStatus from = a.getStatus();
        List<JobApplication> target = column(status, a.getId());
        changeStatus(a, status, note);
        List<JobApplication> ordered = BoardOrder.insert(target, a, position);
        renumber(ordered);
        if (from != status) {
            renumber(column(from, a.getId()));
        }
        return applications.save(a);
    }

    private List<JobApplication> column(ApplicationStatus status, Long exceptId) {
        List<JobApplication> out = new ArrayList<>();
        for (JobApplication x : applications.findByStatus(status)) {
            if (!Objects.equals(x.getId(), exceptId)) {
                out.add(x);
            }
        }
        out.sort(BOARD_ORDER);
        return out;
    }

    private void renumber(List<JobApplication> column) {
        for (int i = 0; i < column.size(); i++) {
            JobApplication x = column.get(i);
            if (!Objects.equals(x.getBoardPosition(), i)) {
                x.setBoardPosition(i);
                applications.save(x);
            }
        }
    }

    /** Czy status oznacza, że dokumenty zostały wysłane do firmy (wszystko poza „do wysłania” i „wycofana”). */
    static boolean isSent(ApplicationStatus status) {
        return status != ApplicationStatus.PLANNED && status != ApplicationStatus.WITHDRAWN;
    }

    private CvVariant currentCv(JobApplication a) {
        if (a.getCvVariantId() == null) {
            return null;
        }
        try {
            return profiles.cv(a.getCvVariantId());
        } catch (NotFoundException e) {
            return null;
        }
    }

    static boolean hasSnapshot(JobApplication a) {
        return a.getSnapshotAt() != null || a.getCvSnapshot() != null;
    }

    /** Snapshot (#46): dokładna treść CV i listu motywacyjnego w chwili wysłania. */
    private static void takeSnapshot(JobApplication a, CvVariant cv) {
        if (cv != null) {
            a.setCvSnapshot(cv.getContent());
        }
        a.setCoverLetterSnapshot(a.getCoverLetter());
        a.setSnapshotAt(Instant.now());
    }

    /** Ręczny snapshot dla aplikacji, które go nie mają (np. utworzonych przed tą funkcją). */
    @Transactional
    public JobApplication snapshotNow(Long id) {
        JobApplication a = get(id);
        if (hasSnapshot(a)) {
            throw new BadRequestException("Snapshot tej aplikacji już istnieje — nie nadpisujemy tego, co wysłano");
        }
        takeSnapshot(a, currentCv(a));
        return applications.save(a);
    }

    /** Zapis listu motywacyjnego do aplikacji (pomysł #42); pusty tekst usuwa list. */
    @Transactional
    public JobApplication saveCoverLetter(Long id, String coverLetter) {
        JobApplication a = get(id);
        a.setCoverLetter(coverLetter == null || coverLetter.isBlank() ? null : coverLetter.strip());
        return applications.save(a);
    }

    @Transactional
    public ApplicationEvent addEvent(Long applicationId, EventType type, String note) {
        get(applicationId);
        return events.save(new ApplicationEvent(applicationId, type, note));
    }

    @Transactional
    public JobApplication save(JobApplication a) {
        return applications.save(a);
    }

    @Transactional
    public void delete(Long id) {
        JobApplication a = get(id);
        events.deleteByApplicationId(id);
        interviews.deleteByApplicationId(id);
        applications.delete(a);
    }
}

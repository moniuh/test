package pl.radarpracy.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.util.List;

/** Operacje na aplikacjach — punkt wejścia dla innych modułów. */
@Service
public class ApplicationService {

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
        a.setContactName(req.contactName());
        a.setContactEmail(req.contactEmail());
        a.setSalaryExpectation(req.salaryExpectation());
        a.setNotes(req.notes());
        a.setBoardPosition((int) applications.count());
        JobApplication saved = applications.save(a);
        ApplicationEvent e = new ApplicationEvent(saved.getId(), EventType.CREATED, null);
        e.setToStatus(saved.getStatus());
        events.save(e);
        return saved;
    }

    @Transactional
    public JobApplication update(Long id, ApplicationRequest req) {
        JobApplication a = get(id);
        if (req.status() != null && req.status() != a.getStatus()) {
            changeStatus(a, req.status(), null);
        }
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
        return applications.save(a);
    }

    @Transactional
    public JobApplication changeStatus(Long id, ApplicationStatus status, String note) {
        JobApplication a = get(id);
        changeStatus(a, status, note);
        return applications.save(a);
    }

    /** Zmiana statusu z wpisem na osi czasu i aktualizacją dat odpowiedzi. */
    void changeStatus(JobApplication a, ApplicationStatus status, String note) {
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
        if (status.isClosed()) {
            a.setNextFollowUpAt(null);
        }
        ApplicationEvent e = new ApplicationEvent(a.getId(), EventType.STATUS_CHANGED, note);
        e.setFromStatus(from);
        e.setToStatus(status);
        events.save(e);
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

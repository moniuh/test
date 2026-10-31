package pl.radarpracy.application;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewRepository interviews;
    private final ApplicationService applications;

    public InterviewController(InterviewRepository interviews, ApplicationService applications) {
        this.interviews = interviews;
        this.applications = applications;
    }

    @GetMapping
    List<Interview> list(@RequestParam(required = false) Long applicationId) {
        return applicationId == null ? interviews.findAllByOrderByScheduledAtAsc()
                : interviews.findByApplicationIdOrderByScheduledAtAsc(applicationId);
    }

    @GetMapping("/{id}")
    Interview get(@PathVariable Long id) {
        return interviews.findById(id).orElseThrow(() -> NotFoundException.of("Rozmowa", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    Interview create(@RequestBody Interview interview) {
        if (interview.getApplicationId() == null || interview.getScheduledAt() == null) {
            throw new BadRequestException("Podaj aplikację i termin rozmowy");
        }
        applications.get(interview.getApplicationId());
        interview.setId(null);
        Interview saved = interviews.save(interview);
        applications.addEvent(saved.getApplicationId(), EventType.INTERVIEW_SCHEDULED,
                "Rozmowa: " + saved.getScheduledAt().toString().replace('T', ' '));
        return saved;
    }

    @PutMapping("/{id}")
    @Transactional
    Interview update(@PathVariable Long id, @RequestBody Interview incoming) {
        Interview i = get(id);
        if (incoming.getScheduledAt() != null) {
            i.setScheduledAt(incoming.getScheduledAt());
        }
        i.setDurationMinutes(incoming.getDurationMinutes());
        i.setKind(incoming.getKind());
        i.setLocation(incoming.getLocation());
        i.setInterviewers(incoming.getInterviewers());
        i.setNotes(incoming.getNotes());
        i.setQuestionsAsked(incoming.getQuestionsAsked());
        i.setWentWell(incoming.getWentWell());
        i.setToImprove(incoming.getToImprove());
        i.setSelfRating(incoming.getSelfRating());
        i.setDebriefedAt(incoming.getDebriefedAt());
        return interviews.save(i);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        interviews.delete(get(id));
    }
}

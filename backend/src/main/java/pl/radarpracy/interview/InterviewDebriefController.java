package pl.radarpracy.interview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.application.Interview;

/** Debrief po rozmowie i wnioski między rozmowami (#62). */
@RestController
public class InterviewDebriefController {

    private final InterviewDebriefService service;

    public InterviewDebriefController(InterviewDebriefService service) {
        this.service = service;
    }

    @PutMapping("/api/interviews/{id}/debrief")
    Interview debrief(@PathVariable Long id, @RequestBody InterviewDebriefService.DebriefRequest request) {
        return service.debrief(id, request);
    }

    @GetMapping("/api/interview-insights")
    InterviewDebriefService.Insights insights() {
        return service.insights();
    }
}

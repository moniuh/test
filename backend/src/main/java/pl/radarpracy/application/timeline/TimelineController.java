package pl.radarpracy.application.timeline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Oś czasu aplikacji (pomysł #40). Notatki dodaje rdzeniowe {@code POST /api/applications/{id}/notes}. */
@RestController
public class TimelineController {

    private final TimelineService service;

    public TimelineController(TimelineService service) {
        this.service = service;
    }

    @GetMapping("/api/applications/{id}/timeline")
    List<TimelineItem> timeline(@PathVariable Long id) {
        return service.timeline(id);
    }
}

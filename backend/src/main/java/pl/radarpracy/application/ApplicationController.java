package pl.radarpracy.application;

import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    List<ApplicationView> list(@RequestParam(required = false) Long offerId) {
        var list = offerId == null ? service.all() : service.forOffer(offerId);
        return list.stream().map(ApplicationView::of).toList();
    }

    @GetMapping("/{id}")
    ApplicationView get(@PathVariable Long id) {
        return ApplicationView.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationView create(@RequestBody ApplicationRequest request) {
        return ApplicationView.of(service.create(request));
    }

    @PutMapping("/{id}")
    ApplicationView update(@PathVariable Long id, @RequestBody ApplicationRequest request) {
        return ApplicationView.of(service.update(id, request));
    }

    record StatusRequest(ApplicationStatus status, String note) {
    }

    @PostMapping("/{id}/status")
    ApplicationView status(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApplicationView.of(service.changeStatus(id, request.status(), request.note()));
    }

    @GetMapping("/{id}/events")
    List<ApplicationEvent> events(@PathVariable Long id) {
        return service.events(id);
    }

    record NoteRequest(String note) {
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationEvent addNote(@PathVariable Long id, @RequestBody NoteRequest request) {
        return service.addEvent(id, EventType.NOTE, request.note());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

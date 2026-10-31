package pl.radarpracy.application.followup;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.application.ApplicationView;

/** Endpointy przypomnień o follow-upie (pomysł #41). */
@RestController
@RequestMapping("/api/applications")
public class FollowUpController {

    private final FollowUpService service;

    public FollowUpController(FollowUpService service) {
        this.service = service;
    }

    /** Zaległe i nadchodzące (w horyzoncie {@code days} dni, domyślnie 14) follow-upy. */
    @GetMapping("/follow-ups")
    FollowUpList list(@RequestParam(required = false) Integer days) {
        return service.list(days);
    }

    record FollowUpRequest(String note) {
    }

    @PostMapping("/{id}/follow-up")
    ApplicationView sent(@PathVariable Long id, @RequestBody(required = false) FollowUpRequest request) {
        return ApplicationView.of(service.sent(id, request == null ? null : request.note()));
    }

    record SnoozeRequest(Integer days) {
    }

    @PostMapping("/{id}/snooze")
    ApplicationView snooze(@PathVariable Long id, @RequestBody SnoozeRequest request) {
        return ApplicationView.of(service.snooze(id, request.days()));
    }
}

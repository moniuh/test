package pl.radarpracy.application.snapshot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** „Co wysłano” (pomysł #46). */
@RestController
public class SnapshotController {

    private final SnapshotService service;

    public SnapshotController(SnapshotService service) {
        this.service = service;
    }

    @GetMapping("/api/applications/{id}/snapshot")
    SnapshotView view(@PathVariable Long id) {
        return service.view(id);
    }

    /** Ręczny snapshot bieżącej wersji CV i listu — tylko gdy aplikacja go jeszcze nie ma. */
    @PostMapping("/api/applications/{id}/snapshot")
    SnapshotView take(@PathVariable Long id) {
        return service.take(id);
    }
}

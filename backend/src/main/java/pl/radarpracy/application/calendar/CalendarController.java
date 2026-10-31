package pl.radarpracy.application.calendar;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Eksport rozmów do kalendarza (pomysł #45). Ścieżki nie kolidują z rdzeniowym
 * {@code /api/interviews/{id}} (dosłowny segment ma pierwszeństwo przed zmienną).
 */
@RestController
public class CalendarController {

    static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    @GetMapping("/api/interviews/{id}/ics")
    ResponseEntity<byte[]> single(@PathVariable Long id) {
        return ics(service.single(id), "rozmowa-" + id + ".ics");
    }

    @GetMapping("/api/interviews/calendar.ics")
    ResponseEntity<byte[]> upcoming() {
        return ics(service.upcoming(), "radar-pracy-rozmowy.ics");
    }

    private static ResponseEntity<byte[]> ics(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}

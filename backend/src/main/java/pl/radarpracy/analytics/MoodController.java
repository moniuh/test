package pl.radarpracy.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.analytics.MoodService.MoodOverview;
import pl.radarpracy.analytics.MoodService.MoodRequest;

import java.time.LocalDate;

/** Dziennik samopoczucia (pomysł #76). */
@RestController
@RequestMapping("/api/mood")
public class MoodController {

    private final MoodService service;

    public MoodController(MoodService service) {
        this.service = service;
    }

    /**
     * @param entry   zapisany wpis
     * @param warning ostrzeżenie po zapisie (null, gdy brak)
     */
    record MoodSaved(MoodEntry entry, MoodRules.Warning warning) {
    }

    /** Historia z ostatnich {@code days} dni (domyślnie 30) i ewentualne ostrzeżenie. */
    @GetMapping
    MoodOverview list(@RequestParam(required = false) Integer days) {
        return service.overview(days, LocalDate.now());
    }

    /** Zapis wpisu; jeden na dzień — ponowny zapis tego samego dnia nadpisuje wpis. */
    @PostMapping
    MoodSaved save(@RequestBody MoodRequest request) {
        LocalDate today = LocalDate.now();
        MoodEntry saved = service.save(request, today);
        return new MoodSaved(saved, service.overview(null, today).warning());
    }
}

package pl.radarpracy.matching;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    /** Pełne dopasowanie jednej oferty z cytatami i wyjaśnieniem. */
    @GetMapping("/api/offers/{id}/match")
    MatchResult match(@PathVariable Long id, @RequestParam(required = false) Long cvId) {
        return service.match(id, cvId);
    }

    /** Skrócone wyniki dla wszystkich ofert. */
    @GetMapping("/api/matches")
    List<MatchSummary> all(@RequestParam(required = false) Long cvId) {
        return service.matchAll(cvId).values().stream().map(MatchSummary::of).toList();
    }
}

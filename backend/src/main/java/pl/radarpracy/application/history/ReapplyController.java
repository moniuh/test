package pl.radarpracy.application.history;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.common.BadRequestException;

/** Sprawdzenie przed aplikowaniem (pomysł #47). */
@RestController
public class ReapplyController {

    private final ReapplyCheckService service;

    public ReapplyController(ReapplyCheckService service) {
        this.service = service;
    }

    @GetMapping("/api/applications/check")
    ReapplyCheck check(@RequestParam(required = false) Long offerId) {
        if (offerId == null) {
            throw new BadRequestException("Podaj ofertę (offerId)");
        }
        return service.check(offerId);
    }
}

package pl.radarpracy.application.letter;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * List motywacyjny oparty na dowodach (pomysł #42). Zapis do aplikacji:
 * {@code PUT /api/applications/{id}/cover-letter} albo pole {@code coverLetter} przy tworzeniu aplikacji.
 */
@RestController
public class CoverLetterController {

    private final CoverLetterService service;

    public CoverLetterController(CoverLetterService service) {
        this.service = service;
    }

    @PostMapping("/api/cover-letters")
    CoverLetterResult generate(@RequestBody CoverLetterRequest request) {
        return service.generate(request);
    }
}

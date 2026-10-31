package pl.radarpracy.offer.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.common.ApiError;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.RejectReason;

import java.util.List;

/**
 * Moduł „Pozyskiwanie i analiza ofert”: parser (#13), import z URL (#14), duplikaty (#16),
 * czerwone flagi (#18), oszustwa (#19), odrzucanie z powodem (#24).
 */
@RestController
@RequestMapping("/api/offers")
public class OfferAnalysisController {

    private final OfferTextParser parser;
    private final OfferImporter importer;
    private final DuplicateService duplicates;
    private final OfferAnalysisService analysis;

    public OfferAnalysisController(OfferTextParser parser, OfferImporter importer, DuplicateService duplicates,
                                   OfferAnalysisService analysis) {
        this.parser = parser;
        this.importer = importer;
        this.duplicates = duplicates;
        this.analysis = analysis;
    }

    record ParseRequest(String text, String url) {
    }

    /** #13: szkic oferty z wklejonej treści (bez zapisu). */
    @PostMapping("/parse")
    ParsedOffer parse(@RequestBody ParseRequest request) {
        if (Text.isBlank(request.text())) {
            throw new BadRequestException("Wklej treść ogłoszenia");
        }
        if (request.text().length() > OfferTextParser.MAX_TEXT) {
            throw new BadRequestException("Treść jest za długa (limit " + OfferTextParser.MAX_TEXT + " znaków)");
        }
        return parser.parse(request.text(), request.url());
    }

    record ImportUrlRequest(String url) {
    }

    /** #14: pobranie strony ogłoszenia (JSON-LD JobPosting albo treść strony) i szkic oferty. */
    @PostMapping("/import-url")
    ParsedOffer importUrl(@RequestBody ImportUrlRequest request) {
        return importer.importUrl(request.url());
    }

    /** Strona nie dała się pobrać — 422 z podpowiedzią, by wkleić treść ręcznie. */
    @ExceptionHandler(OfferImportException.class)
    ResponseEntity<ApiError> importFailed(OfferImportException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("import_failed", e.getMessage(), List.of(e.reason(), OfferImportException.PASTE_HINT)));
    }

    /** #16: oferty podobne do zapisanej. */
    @GetMapping("/{id}/duplicates")
    DuplicateReport duplicates(@PathVariable Long id) {
        return duplicates.forOffer(id);
    }

    record DuplicateCheckRequest(String title, String company, String description, String url, Long excludeId) {
    }

    /** #16: ostrzeżenie przed zapisem — czy podobna oferta już istnieje. */
    @PostMapping("/duplicates/check")
    DuplicateReport checkDuplicates(@RequestBody DuplicateCheckRequest r) {
        return duplicates.check(r.title(), r.company(), r.description(), r.url(), r.excludeId());
    }

    record MarkDuplicateRequest(Long originalId) {
    }

    /** #16: oznaczenie oferty jako duplikatu (status ARCHIVED, powód DUPLICATE). */
    @PostMapping("/{id}/mark-duplicate")
    JobOffer markDuplicate(@PathVariable Long id, @RequestBody MarkDuplicateRequest r) {
        return duplicates.markDuplicate(id, r.originalId());
    }

    /** #16: cofnięcie oznaczenia duplikatu. */
    @PostMapping("/{id}/unmark-duplicate")
    JobOffer unmarkDuplicate(@PathVariable Long id) {
        return duplicates.unmarkDuplicate(id);
    }

    /** #18 + #19 + #16: zbiorcza analiza ogłoszenia. */
    @GetMapping("/{id}/analysis")
    OfferAnalysis analysis(@PathVariable Long id) {
        return analysis.analyze(id);
    }

    record RejectRequest(RejectReason reason, String note) {
    }

    /** #24: odrzucenie oferty z powodem. */
    @PostMapping("/{id}/reject")
    JobOffer reject(@PathVariable Long id, @RequestBody RejectRequest r) {
        return analysis.reject(id, r.reason(), r.note());
    }
}

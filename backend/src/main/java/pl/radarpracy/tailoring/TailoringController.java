package pl.radarpracy.tailoring;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.tailoring.TailoringDtos.CheckRequest;
import pl.radarpracy.tailoring.TailoringDtos.CheckResult;
import pl.radarpracy.tailoring.TailoringDtos.DeleteResult;
import pl.radarpracy.tailoring.TailoringDtos.Detail;
import pl.radarpracy.tailoring.TailoringDtos.LibraryItem;
import pl.radarpracy.tailoring.TailoringDtos.OfferOption;
import pl.radarpracy.tailoring.TailoringDtos.Preview;
import pl.radarpracy.tailoring.TailoringDtos.PreviewRequest;
import pl.radarpracy.tailoring.TailoringDtos.SaveRequest;

import java.util.List;

/** REST tailoringu CV: {@code /api/tailoring…}. */
@RestController
@RequestMapping("/api/tailoring")
public class TailoringController {

    static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final TailoringService service;

    public TailoringController(TailoringService service) {
        this.service = service;
    }

    /** Podgląd dopasowanego CV bez zapisu. */
    @PostMapping("/preview")
    Preview preview(@RequestBody PreviewRequest req) {
        return service.preview(req);
    }

    /** Kontrola tekstu po ręcznej edycji (bez zapisu). */
    @PostMapping("/check")
    CheckResult check(@RequestBody CheckRequest req) {
        return service.check(req);
    }

    /** Zapis jako nowa wersja CV (po kontroli strażnika). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Detail save(@RequestBody SaveRequest req) {
        return service.save(req);
    }

    /** Biblioteka dopasowanych CV (opcjonalnie dla jednej oferty). */
    @GetMapping
    List<LibraryItem> list(@RequestParam(required = false) Long offerId) {
        return service.list(offerId);
    }

    /** Oferty do dopasowania (NEW/SAVED), od najlepiej dopasowanej. */
    @GetMapping("/offers")
    List<OfferOption> offers() {
        return service.offerOptions();
    }

    @GetMapping("/{cvVariantId}")
    Detail detail(@PathVariable Long cvVariantId) {
        return service.detail(cvVariantId);
    }

    @DeleteMapping("/{cvVariantId}")
    DeleteResult delete(@PathVariable Long cvVariantId) {
        return service.delete(cvVariantId);
    }

    @GetMapping("/{cvVariantId}/docx")
    ResponseEntity<byte[]> docx(@PathVariable Long cvVariantId) {
        TailoringService.Docx file = service.docx(cvVariantId);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename()).build().toString())
                .body(file.bytes());
    }

    /** Odpowiedź przy odrzuceniu zapisu przez strażnika: format błędów API + pełny raport. */
    public record GuardError(String error, String message, List<String> details, GuardReport report) {
    }

    @ExceptionHandler(GuardRejectedException.class)
    ResponseEntity<GuardError> guardRejected(GuardRejectedException e) {
        List<String> details = e.report().violations().stream()
                .map(v -> (v.line() > 0 ? "linia " + v.line() + ": " : "") + v.detail())
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new GuardError("guard_violations", e.getMessage(), details, e.report()));
    }
}

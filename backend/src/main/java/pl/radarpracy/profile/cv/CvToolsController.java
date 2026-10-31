package pl.radarpracy.profile.cv;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.radarpracy.common.BadRequestException;

import java.io.IOException;

/**
 * Narzędzia CV: import z pliku (#1), umiejętności z CV (#2), audyt ATS (#4), tailoring pod ofertę (#5)
 * i dane wrażliwe (#8).
 */
@RestController
public class CvToolsController {

    private final CvImportService importer;
    private final CvSkillExtractor skills;
    private final CvAuditService audit;
    private final SensitiveDataDetector sensitive;
    private final CvTailoringService tailoring;

    public CvToolsController(CvImportService importer, CvSkillExtractor skills, CvAuditService audit,
                             SensitiveDataDetector sensitive, CvTailoringService tailoring) {
        this.importer = importer;
        this.skills = skills;
        this.audit = audit;
        this.sensitive = sensitive;
        this.tailoring = tailoring;
    }

    /** Import CV z PDF lub DOCX jako nowej wersji CV. */
    @PostMapping("/api/cv/import")
    @ResponseStatus(HttpStatus.CREATED)
    CvImportService.CvImportResult importCv(@RequestParam(value = "file", required = false) MultipartFile file,
                                            @RequestParam(value = "name", required = false) String name,
                                            @RequestParam(value = "targetRole", required = false) String targetRole) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Wybierz plik CV w formacie PDF lub DOCX.");
        }
        if (file.getSize() > CvTextExtractor.MAX_BYTES) {
            throw new BadRequestException("Plik jest za duży. Limit to " + (CvTextExtractor.MAX_BYTES / 1024 / 1024)
                    + " MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Nie udało się odebrać pliku — spróbuj ponownie.");
        }
        return importer.importFile(file.getOriginalFilename(), bytes, name, targetRole);
    }

    /** Umiejętności rozpoznane w CV jako propozycje do profilu. */
    @GetMapping("/api/cv/{id}/skills")
    CvSkillExtractor.CvSkillsResult skills(@PathVariable Long id) {
        return skills.suggestions(id);
    }

    /** Dopisuje wybrane umiejętności z CV do profilu. */
    @PostMapping("/api/cv/{id}/skills/apply")
    CvSkillExtractor.ApplyResult applySkills(@PathVariable Long id,
                                             @RequestBody(required = false) CvSkillExtractor.ApplyRequest request) {
        return skills.apply(id, request == null ? null : request.names());
    }

    /** Audyt CV pod ATS: wynik 0–100 i lista kontroli. */
    @GetMapping("/api/cv/{id}/audit")
    CvAuditService.CvAudit audit(@PathVariable Long id) {
        return audit.audit(id);
    }

    /** Zbędne dane wrażliwe w CV. */
    @GetMapping("/api/cv/{id}/sensitive")
    SensitiveDataDetector.SensitiveReport sensitive(@PathVariable Long id) {
        return sensitive.detect(id);
    }

    /** Tailoring CV pod ofertę: co dopisać z profilu, co opisać jako pokrewne, czego brakuje. */
    @GetMapping("/api/offers/{offerId}/tailoring")
    CvTailoringService.TailoringResult tailoring(@PathVariable Long offerId,
                                                 @RequestParam(required = false) Long cvId) {
        return tailoring.tailor(offerId, cvId);
    }
}

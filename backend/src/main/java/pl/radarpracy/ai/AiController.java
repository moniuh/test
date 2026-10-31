package pl.radarpracy.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.profile.ProfileService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AiController {

    private final ClaudeClient claude;
    private final PiiMasker masker;
    private final ProfileService profiles;

    public AiController(ClaudeClient claude, PiiMasker masker, ProfileService profiles) {
        this.claude = claude;
        this.masker = masker;
        this.profiles = profiles;
    }

    @GetMapping("/api/ai/status")
    Map<String, Object> status() {
        AiStatus s = claude.status();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", s.available());
        out.put("model", s.model());
        out.put("reason", s.reason());
        out.put("maskedCategories", PiiMasker.categories());
        return out;
    }

    record TextRequest(String text) {
    }

    /** Podgląd: co zobaczyłby model po zamaskowaniu danych osobowych (pomysł #82). */
    @PostMapping("/api/ai/mask-preview")
    Map<String, Object> maskPreview(@RequestBody TextRequest request) {
        PiiMasker.Masked masked = masker.mask(request.text(), profiles.profile());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("masked", masked.text());
        out.put("summary", masked.summary());
        return out;
    }
}

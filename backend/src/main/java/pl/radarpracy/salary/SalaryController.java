package pl.radarpracy.salary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.offer.OfferService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SalaryController {

    private final SalaryNormalizer normalizer;
    private final OfferService offers;

    public SalaryController(SalaryNormalizer normalizer, OfferService offers) {
        this.normalizer = normalizer;
        this.offers = offers;
    }

    @GetMapping("/api/offers/{id}/salary")
    NormalizedSalary offerSalary(@PathVariable Long id) {
        return normalizer.normalize(offers.get(id));
    }

    record TextRequest(String text) {
    }

    /** Rozpoznanie i normalizacja widełek z dowolnego tekstu. */
    @PostMapping("/api/salary/parse")
    Map<String, Object> parse(@RequestBody TextRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        var range = SalaryParser.parse(request.text());
        out.put("range", range.orElse(null));
        out.put("normalized", range.map(normalizer::normalize).orElse(NormalizedSalary.absent()));
        return out;
    }
}

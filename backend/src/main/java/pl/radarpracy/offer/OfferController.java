package pl.radarpracy.offer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/offers")
public class OfferController {

    private final OfferService service;
    private final RequirementExtractor extractor;

    public OfferController(OfferService service, RequirementExtractor extractor) {
        this.service = service;
        this.extractor = extractor;
    }

    @GetMapping
    List<JobOffer> list(@RequestParam(required = false) OfferStatus status) {
        return status == null ? service.all() : service.byStatus(status);
    }

    @GetMapping("/{id}")
    JobOffer get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JobOffer create(@RequestBody JobOffer offer) {
        return service.create(offer);
    }

    @PutMapping("/{id}")
    JobOffer update(@PathVariable Long id, @RequestBody JobOffer offer) {
        return service.update(id, offer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    record StatusRequest(OfferStatus status) {
    }

    @PostMapping("/{id}/status")
    JobOffer status(@PathVariable Long id, @RequestBody StatusRequest request) {
        return service.setStatus(id, request.status());
    }

    @PostMapping("/{id}/requirements/reextract")
    JobOffer reextract(@PathVariable Long id) {
        return service.reextractRequirements(id);
    }

    record TextRequest(String text) {
    }

    /** Podgląd wymagań wyodrębnionych z dowolnego tekstu (bez zapisu). */
    @PostMapping("/requirements/extract")
    Map<String, List<Requirement>> extract(@RequestBody TextRequest request) {
        return Map.of("requirements", extractor.extract(request.text()));
    }
}

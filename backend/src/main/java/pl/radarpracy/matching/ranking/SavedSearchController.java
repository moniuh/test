package pl.radarpracy.matching.ranking;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchRequest;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchResults;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchView;

import java.util.List;

/** Zapisane wyszukiwania z alertami o nowych ofertach (pomysł #33). */
@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService service;

    public SavedSearchController(SavedSearchService service) {
        this.service = service;
    }

    /** Lista z licznikami: wszystkich wyników i nowych od ostatniego przejrzenia. */
    @GetMapping
    List<SavedSearchView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    SavedSearchView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedSearchView create(@RequestBody SavedSearchRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    SavedSearchView update(@PathVariable Long id, @RequestBody SavedSearchRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Wyniki w kolejności rankingu oraz id nowych ofert (nie zmienia stanu „przejrzane”). */
    @GetMapping("/{id}/results")
    SavedSearchResults results(@PathVariable Long id) {
        return service.results(id);
    }

    /** Oznacza wyniki jako przejrzane. */
    @PostMapping("/{id}/mark-seen")
    SavedSearchView markSeen(@PathVariable Long id) {
        return service.markSeen(id);
    }
}

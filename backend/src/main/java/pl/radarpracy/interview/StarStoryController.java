package pl.radarpracy.interview;

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

/** Bank historii STAR (#56). */
@RestController
@RequestMapping("/api/star-stories")
public class StarStoryController {

    private final StarStoryService service;

    public StarStoryController(StarStoryService service) {
        this.service = service;
    }

    @GetMapping
    List<StarStoryService.StarStoryView> list() {
        return service.all();
    }

    @GetMapping("/suggest")
    StarStoryService.Suggestions suggest(@RequestParam Long offerId) {
        return service.suggest(offerId);
    }

    @GetMapping("/{id}")
    StarStoryService.StarStoryView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StarStoryService.StarStoryView create(@RequestBody StarStoryService.StarStoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    StarStoryService.StarStoryView update(@PathVariable Long id,
                                          @RequestBody StarStoryService.StarStoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

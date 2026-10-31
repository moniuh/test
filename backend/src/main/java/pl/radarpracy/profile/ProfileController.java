package pl.radarpracy.profile;

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

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    CandidateProfile profile() {
        return service.profile();
    }

    @PutMapping("/profile")
    CandidateProfile update(@RequestBody CandidateProfile profile) {
        return service.update(profile);
    }

    @GetMapping("/cv")
    List<CvVariant> cvs() {
        return service.cvVariants();
    }

    @GetMapping("/cv/{id}")
    CvVariant cv(@PathVariable Long id) {
        return service.cv(id);
    }

    @PostMapping("/cv")
    @ResponseStatus(HttpStatus.CREATED)
    CvVariant create(@RequestBody CvVariant cv) {
        return service.createCv(cv);
    }

    @PutMapping("/cv/{id}")
    CvVariant update(@PathVariable Long id, @RequestBody CvVariant cv) {
        return service.updateCv(id, cv);
    }

    @DeleteMapping("/cv/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.deleteCv(id);
    }

    @PostMapping("/cv/{id}/activate")
    CandidateProfile activate(@PathVariable Long id) {
        return service.activateCv(id);
    }
}

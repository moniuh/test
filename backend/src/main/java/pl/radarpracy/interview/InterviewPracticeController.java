package pl.radarpracy.interview;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Pytania z oferty (#53) i trening odpowiedzi metodą STAR (#54). */
@RestController
public class InterviewPracticeController {

    private final InterviewQuestionService questions;
    private final InterviewPracticeService practice;

    public InterviewPracticeController(InterviewQuestionService questions, InterviewPracticeService practice) {
        this.questions = questions;
        this.practice = practice;
    }

    @GetMapping("/api/offers/{id}/interview-questions")
    List<InterviewQuestion> questions(@PathVariable Long id, @RequestParam(required = false) Long cvId) {
        return questions.forOffer(id, cvId);
    }

    @PostMapping("/api/interview-practice/assess")
    StarAssessment assess(@RequestBody InterviewPracticeService.AssessRequest request) {
        return practice.assess(request);
    }

    @GetMapping("/api/interview-practice")
    List<PracticeAnswer> history() {
        return practice.history();
    }

    @DeleteMapping("/api/interview-practice/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        practice.delete(id);
    }
}

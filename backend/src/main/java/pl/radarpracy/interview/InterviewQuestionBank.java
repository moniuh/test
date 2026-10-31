package pl.radarpracy.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Bank pytań rekrutacyjnych z zasobu {@code classpath:interview/pytania.json} (pomysł #53):
 * pytania per umiejętność, per kategoria słownika, szablony luk, pytania behawioralne i motywacyjne.
 */
@Component
public class InterviewQuestionBank {

    /** Szablon pytania z podpowiedzią. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Template(String q, String tip) {
    }

    /** Pytanie wyzwalane słowami kluczowymi wymagania (np. „odporność na stres”). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeywordTemplate(List<String> match, String category, String q, String tip) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Map<String, List<Template>> skills, Map<String, List<Template>> categories,
                List<Template> fallbackTechnical, List<Template> gap, List<Template> partial,
                Map<String, List<Template>> soft, List<KeywordTemplate> keywords,
                List<Template> requirementBehavioral, List<Template> behavioral, List<Template> motivational,
                List<Template> company) {
    }

    private final Data data;

    public InterviewQuestionBank() {
        this(load("interview/pytania.json"));
    }

    InterviewQuestionBank(Data data) {
        this.data = data;
    }

    private static Data load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return JsonMapper.builder().build().readValue(in, Data.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie można wczytać banku pytań " + path, e);
        }
    }

    public List<Template> forSkill(String skill) {
        return data.skills().getOrDefault(skill, List.of());
    }

    public List<Template> forCategory(String category) {
        return category == null ? List.of() : data.categories().getOrDefault(category, List.of());
    }

    public List<Template> fallbackTechnical() {
        return data.fallbackTechnical();
    }

    public List<Template> gap() {
        return data.gap();
    }

    public List<Template> partial() {
        return data.partial();
    }

    public List<Template> forSoftSkill(String skill) {
        return data.soft().getOrDefault(skill, List.of());
    }

    public List<KeywordTemplate> keywords() {
        return data.keywords();
    }

    public List<Template> requirementBehavioral() {
        return data.requirementBehavioral();
    }

    public List<Template> behavioral() {
        return data.behavioral();
    }

    public List<Template> motivational() {
        return data.motivational();
    }

    public List<Template> company() {
        return data.company();
    }
}

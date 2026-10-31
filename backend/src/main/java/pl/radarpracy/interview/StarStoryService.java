package pl.radarpracy.interview;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.skills.SkillDictionary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bank historii STAR (pomysł #56): CRUD i podpowiedzi historii pasujących do wymagań oferty. */
@Service
public class StarStoryService {

    static final int MUST_WEIGHT = 3;
    static final int NICE_WEIGHT = 1;
    private static final Set<String> NOT_FOR_STORIES = Set.of("wyksztalcenie", "jezyk-obcy", "uprawnienia");

    /** Dane historii od użytkownika. */
    public record StarStoryRequest(String title, String situation, String task, String action, String result,
                                   List<String> skills) {
    }

    /**
     * Historia z umiejętnościami rozpoznanymi w treści.
     *
     * @param skills         umiejętności wskazane przez użytkownika (nazwy kanoniczne, gdy rozpoznane)
     * @param detectedSkills umiejętności rozpoznane słownikiem w treści historii (bez wskazanych)
     */
    public record StarStoryView(Long id, String title, String situation, String task, String action, String result,
                                List<String> skills, List<String> detectedSkills, Instant createdAt,
                                Instant updatedAt) {
    }

    /** Historia pasująca do oferty. */
    public record StorySuggestion(StarStoryView story, int score, List<String> matchedSkills,
                                  List<String> requirements) {
    }

    /** Odpowiedź {@code GET /api/star-stories/suggest}. */
    public record Suggestions(Long offerId, String offerTitle, List<StorySuggestion> suggestions,
                              List<String> uncoveredSkills) {
    }

    private final StarStoryRepository repository;
    private final SkillDictionary dictionary;
    private final OfferService offers;

    public StarStoryService(StarStoryRepository repository, SkillDictionary dictionary, OfferService offers) {
        this.repository = repository;
        this.dictionary = dictionary;
        this.offers = offers;
    }

    public List<StarStoryView> all() {
        return repository.findAllByOrderByIdAsc().stream().map(this::view).toList();
    }

    public StarStoryView get(Long id) {
        return view(find(id));
    }

    @Transactional
    public StarStoryView create(StarStoryRequest req) {
        StarStory s = new StarStory();
        apply(req, s);
        return view(repository.save(s));
    }

    @Transactional
    public StarStoryView update(Long id, StarStoryRequest req) {
        StarStory s = find(id);
        apply(req, s);
        return view(repository.save(s));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    public Suggestions suggest(Long offerId) {
        JobOffer offer = offers.get(offerId);
        return suggest(offer, repository.findAllByOrderByIdAsc());
    }

    Suggestions suggest(JobOffer offer, List<StarStory> stories) {
        Map<StarStory, Set<String>> effective = new LinkedHashMap<>();
        for (StarStory s : stories) {
            effective.put(s, effectiveSkills(s));
        }
        List<StorySuggestion> out = new ArrayList<>();
        for (StarStory s : stories) {
            Set<String> has = effective.get(s);
            int score = 0;
            Set<String> matched = new LinkedHashSet<>();
            List<String> reqs = new ArrayList<>();
            for (Requirement r : offer.getRequirements()) {
                Set<String> common = new LinkedHashSet<>(dictionary.skillsIn(r.text()));
                common.retainAll(has);
                if (!common.isEmpty()) {
                    score += r.mustHave() ? MUST_WEIGHT : NICE_WEIGHT;
                    matched.addAll(common);
                    reqs.add(r.text());
                }
            }
            if (score > 0) {
                out.add(new StorySuggestion(view(s), score, List.copyOf(matched), reqs));
            }
        }
        out.sort(Comparator.comparingInt(StorySuggestion::score).reversed()
                .thenComparing(x -> x.story().id(), Comparator.nullsLast(Comparator.naturalOrder())));

        Set<String> covered = new LinkedHashSet<>();
        effective.values().forEach(covered::addAll);
        Set<String> uncovered = new LinkedHashSet<>();
        for (Requirement r : offer.getRequirements()) {
            if (!r.mustHave()) {
                continue;
            }
            for (String skill : dictionary.skillsIn(r.text())) {
                boolean storyable = dictionary.get(skill).map(sk -> !NOT_FOR_STORIES.contains(sk.category())).orElse(true);
                if (storyable && !covered.contains(skill)) {
                    uncovered.add(skill);
                }
            }
        }
        return new Suggestions(offer.getId(), offer.getTitle(), out, List.copyOf(uncovered));
    }

    /** Umiejętności wskazane + rozpoznane w treści + obejmowane (Spring Boot ⇒ Spring, Java). */
    Set<String> effectiveSkills(StarStory s) {
        Set<String> out = new LinkedHashSet<>();
        for (String skill : s.getSkills()) {
            out.add(dictionary.canonicalName(skill));
        }
        out.addAll(dictionary.skillsIn(s.fullText()));
        for (String skill : new ArrayList<>(out)) {
            out.addAll(dictionary.impliedBy(skill));
        }
        return out;
    }

    private void apply(StarStoryRequest req, StarStory s) {
        if (req == null || Text.isBlank(req.title())) {
            throw new BadRequestException("Podaj tytuł historii");
        }
        if (Text.isBlank(req.situation()) && Text.isBlank(req.task()) && Text.isBlank(req.action())
                && Text.isBlank(req.result())) {
            throw new BadRequestException("Uzupełnij przynajmniej jeden element STAR");
        }
        s.setTitle(Text.truncate(req.title().strip(), 255));
        s.setSituation(req.situation());
        s.setTask(req.task());
        s.setAction(req.action());
        s.setResult(req.result());
        Set<String> skills = new LinkedHashSet<>();
        if (req.skills() != null) {
            for (String skill : req.skills()) {
                if (!Text.isBlank(skill)) {
                    skills.add(dictionary.canonicalName(skill));
                }
            }
        }
        s.setSkills(new ArrayList<>(skills));
    }

    private StarStory find(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Historia STAR", id));
    }

    private StarStoryView view(StarStory s) {
        List<String> detected = dictionary.skillsIn(s.fullText()).stream()
                .filter(x -> !s.getSkills().contains(x)).toList();
        return new StarStoryView(s.getId(), s.getTitle(), s.getSituation(), s.getTask(), s.getAction(), s.getResult(),
                List.copyOf(s.getSkills()), detected, s.getCreatedAt(), s.getUpdatedAt());
    }
}

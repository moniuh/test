package pl.radarpracy.interview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.radarpracy.ai.ClaudeClient;
import pl.radarpracy.ai.LlmUnavailableException;
import pl.radarpracy.ai.PiiMasker;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.skills.SkillDictionary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Trening odpowiedzi (pomysł #54): ocena heurystyczna STAR, opcjonalny komentarz AI i historia prób.
 *
 * <p>Wynik liczbowy zawsze pochodzi z {@link StarAnalyzer}. Model AI (gdy dostępny) dopisuje tylko
 * komentarz słowny — na danych zamaskowanych przez {@link PiiMasker}.</p>
 */
@Service
public class InterviewPracticeService {

    private static final Logger log = LoggerFactory.getLogger(InterviewPracticeService.class);
    static final int MAX_ANSWER_LENGTH = 10_000;

    static final String AI_SYSTEM = """
            Jesteś trenerem rozmów rekrutacyjnych. Oceń odpowiedź kandydata metodą STAR \
            (sytuacja, zadanie, działanie, rezultat). Wskaż jedną mocną stronę i 2–3 konkretne poprawki, \
            np. jak dodać mierzalny rezultat albo podkreślić własny wkład. Nie podawaj oceny liczbowej — \
            wynik liczy aplikacja. Odpowiedz w maksymalnie 120 słowach, w punktach. Zwracaj się do kandydata \
            w formie neutralnej płciowo.
            """ + ClaudeClient.DATA_RULES;

    /**
     * Żądanie oceny.
     *
     * @param question pytanie
     * @param answer   odpowiedź kandydata
     * @param offerId  oferta, do której ćwiczymy (opcjonalnie — sprawdzamy nawiązania do wymagań)
     * @param save     czy zapisać próbę w historii (domyślnie tak)
     * @param ai       czy poprosić model o komentarz, jeśli jest dostępny (domyślnie tak)
     */
    public record AssessRequest(String question, String answer, Long offerId, Boolean save, Boolean ai) {
    }

    private final StarAnalyzer analyzer;
    private final PracticeAnswerRepository repository;
    private final OfferService offers;
    private final SkillDictionary dictionary;
    private final ClaudeClient claude;
    private final PiiMasker masker;
    private final ProfileService profiles;

    public InterviewPracticeService(StarAnalyzer analyzer, PracticeAnswerRepository repository, OfferService offers,
                                    SkillDictionary dictionary, ClaudeClient claude, PiiMasker masker,
                                    ProfileService profiles) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.offers = offers;
        this.dictionary = dictionary;
        this.claude = claude;
        this.masker = masker;
        this.profiles = profiles;
    }

    public StarAssessment assess(AssessRequest req) {
        if (req == null || Text.isBlank(req.question())) {
            throw new BadRequestException("Podaj pytanie, na które odpowiadasz");
        }
        if (Text.isBlank(req.answer())) {
            throw new BadRequestException("Wpisz odpowiedź do oceny");
        }
        if (req.answer().length() > MAX_ANSWER_LENGTH) {
            throw new BadRequestException("Odpowiedź jest za długa (limit " + MAX_ANSWER_LENGTH + " znaków)");
        }
        JobOffer offer = req.offerId() == null ? null : offers.get(req.offerId());
        Set<String> offerSkills = new LinkedHashSet<>();
        Set<String> mustSkills = new LinkedHashSet<>();
        if (offer != null) {
            for (Requirement r : offer.getRequirements()) {
                Set<String> skills = dictionary.skillsIn(r.text());
                offerSkills.addAll(skills);
                if (r.mustHave()) {
                    mustSkills.addAll(skills);
                }
            }
        }
        StarAssessment assessment = analyzer.analyze(req.answer(), offerSkills, mustSkills);

        String aiComment = null;
        if (!Boolean.FALSE.equals(req.ai()) && claude.available()) {
            aiComment = aiComment(req.question(), req.answer(), offer, assessment);
        }
        Long id = null;
        if (!Boolean.FALSE.equals(req.save())) {
            PracticeAnswer p = new PracticeAnswer();
            p.setQuestion(req.question().strip());
            p.setAnswer(req.answer());
            p.setOfferId(offer == null ? null : offer.getId());
            p.setScore(assessment.score());
            p.setSituation(assessment.star().situation().detected());
            p.setTask(assessment.star().task().detected());
            p.setAction(assessment.star().action().detected());
            p.setResult(assessment.star().result().detected());
            p.setHasNumbers(assessment.hasNumbers());
            p.setWordCount(assessment.wordCount());
            p.setFeedback(assessment.feedback());
            p.setAiComment(aiComment);
            id = repository.save(p).getId();
        }
        return assessment.withAi(aiComment, id);
    }

    /** Komentarz modelu — tylko na zamaskowanych danych; przy jakimkolwiek problemie null (ścieżka offline). */
    String aiComment(String question, String answer, JobOffer offer, StarAssessment assessment) {
        CandidateProfile profile = profiles.profile();
        PiiMasker.Masked q = masker.mask(question, profile);
        PiiMasker.Masked a = masker.mask(answer, profile);
        StringBuilder user = new StringBuilder();
        user.append(ClaudeClient.asData("pytanie", q.text())).append("\n\n");
        user.append(ClaudeClient.asData("odpowiedz", a.text())).append("\n\n");
        if (offer != null) {
            StringBuilder o = new StringBuilder(Text.nullToEmpty(offer.getTitle())).append('\n');
            offer.getRequirements().forEach(r -> o.append("- ").append(r.text()).append('\n'));
            user.append(ClaudeClient.asData("oferta", masker.mask(o.toString(), profile).text())).append("\n\n");
        }
        var star = assessment.star();
        user.append("Reguły aplikacji wykryły: sytuacja ").append(yesNo(star.situation().detected()))
                .append(", zadanie ").append(yesNo(star.task().detected()))
                .append(", działanie ").append(yesNo(star.action().detected()))
                .append(", rezultat ").append(yesNo(star.result().detected()))
                .append(", liczby ").append(yesNo(assessment.hasNumbers()))
                .append(". Napisz komentarz trenera.");
        try {
            String raw = claude.complete(AI_SYSTEM, user.toString());
            return a.unmask(q.unmask(raw));
        } catch (LlmUnavailableException e) {
            log.info("Komentarz AI niedostępny — używam tylko reguł: {}", e.getMessage());
            return null;
        }
    }

    public List<PracticeAnswer> history() {
        return repository.findAllByOrderByCreatedAtDescIdDesc();
    }

    public void delete(Long id) {
        PracticeAnswer p = repository.findById(id).orElseThrow(() -> NotFoundException.of("Próba odpowiedzi", id));
        repository.delete(p);
    }

    private static String yesNo(boolean b) {
        return b ? "tak" : "nie";
    }
}

package pl.radarpracy.interview;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.application.InterviewRepository;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.skills.SkillDictionary;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Debrief po rozmowie i wnioski zbierane między rozmowami (pomysł #62).
 *
 * <p>Korzysta z rdzeniowej encji {@link Interview} (pola wentWell, toImprove, selfRating, questionsAsked,
 * debriefedAt). Wnioski liczy kod: średnia samoocena, tematy powtarzające się w „do poprawy”
 * (umiejętności ze słownika i słowa) oraz bank pytań, które realnie padły.</p>
 */
@Service
public class InterviewDebriefService {

    static final int TOPIC_STEM = 6;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}+#]+");
    /** Słowa, które w „do poprawy” niczego nie mówią o temacie. */
    private static final Set<String> GENERIC = Set.of("trzeba", "bardziej", "lepiej", "przygotowac", "przygotowa",
            "rozmowy", "rozmowie", "rozmowa", "pytania", "pytanie", "pytaniach", "wczesniej", "mowilam", "mowilem",
            "zbyt", "dobrze", "musze", "powinnam", "powinienem", "nadrobic", "znam", "tylko", "jeszcze", "bylo",
            "byla", "kilka", "temat", "tematu", "about", "should", "better", "more", "prepare");
    private static final Pattern BULLET = Pattern.compile("^(?:[-•*·–—]|\\d{1,2}[.)])\\s*");

    /** Dane debriefu. */
    public record DebriefRequest(String wentWell, String toImprove, Integer selfRating, String questionsAsked) {
    }

    /** Rozmowa z danymi aplikacji i oferty. */
    public record InterviewRef(Long interviewId, Long applicationId, Long offerId, String offerTitle, String company,
                               LocalDateTime scheduledAt, InterviewKind kind, Integer durationMinutes, String location,
                               String interviewers, ApplicationStatus applicationStatus, Integer selfRating,
                               Instant debriefedAt) {
    }

    /** Temat powtarzający się w „do poprawy”. */
    public record Topic(String topic, String kind, int count, List<Long> interviewIds) {
    }

    /** Pytanie, które padło na rozmowie. */
    public record AskedQuestion(String question, Long interviewId, Long applicationId, Long offerId,
                                String offerTitle, String company, LocalDateTime date, List<String> skills) {
    }

    /** Samoocena w czasie. */
    public record RatingPoint(Long interviewId, LocalDateTime date, int rating, String offerTitle) {
    }

    /** Odpowiedź {@code GET /api/interview-insights}. */
    public record Insights(List<InterviewRef> upcoming, List<InterviewRef> pendingDebriefs, List<InterviewRef> past,
                           int debriefedCount,
                           Double averageSelfRating, List<RatingPoint> ratings, List<Topic> recurringTopics,
                           List<AskedQuestion> askedQuestions, List<String> notes) {
    }

    private final InterviewRepository interviews;
    private final ApplicationService applications;
    private final SkillDictionary dictionary;

    public InterviewDebriefService(InterviewRepository interviews, ApplicationService applications,
                                   SkillDictionary dictionary) {
        this.interviews = interviews;
        this.applications = applications;
        this.dictionary = dictionary;
    }

    @Transactional
    public Interview debrief(Long interviewId, DebriefRequest req) {
        Interview i = interviews.findById(interviewId).orElseThrow(() -> NotFoundException.of("Rozmowa", interviewId));
        if (req == null || req.selfRating() == null || req.selfRating() < 1 || req.selfRating() > 5) {
            throw new BadRequestException("Oceń rozmowę w skali 1–5");
        }
        boolean first = i.getDebriefedAt() == null;
        i.setWentWell(blankToNull(req.wentWell()));
        i.setToImprove(blankToNull(req.toImprove()));
        i.setSelfRating(req.selfRating());
        if (req.questionsAsked() != null) {
            i.setQuestionsAsked(blankToNull(req.questionsAsked()));
        }
        i.setDebriefedAt(Instant.now());
        Interview saved = interviews.save(i);
        if (first) {
            applications.addEvent(saved.getApplicationId(), EventType.INTERVIEW_DONE,
                    "Podsumowanie rozmowy: samoocena " + req.selfRating() + "/5");
        }
        return saved;
    }

    public Insights insights() {
        return insights(interviews.findAllByOrderByScheduledAtAsc(), LocalDateTime.now());
    }

    Insights insights(List<Interview> all, LocalDateTime now) {
        Map<Long, JobApplication> apps = new HashMap<>();
        for (JobApplication a : applications.all()) {
            apps.put(a.getId(), a);
        }
        List<InterviewRef> upcoming = new ArrayList<>();
        List<InterviewRef> pending = new ArrayList<>();
        List<InterviewRef> past = new ArrayList<>();
        List<RatingPoint> ratings = new ArrayList<>();
        List<AskedQuestion> asked = new ArrayList<>();
        Map<String, Topic> topics = new LinkedHashMap<>();
        Map<String, String> topicLabels = new HashMap<>();
        Map<String, Set<Long>> skillTopics = new LinkedHashMap<>();
        Map<String, Set<Long>> wordTopics = new LinkedHashMap<>();
        int debriefed = 0;
        int ratingSum = 0;
        int ratingCount = 0;

        for (Interview i : all) {
            InterviewRef ref = ref(i, apps.get(i.getApplicationId()));
            int duration = i.getDurationMinutes() == null ? 60 : i.getDurationMinutes();
            if (!i.getScheduledAt().isBefore(now)) {
                upcoming.add(ref);
            } else {
                past.add(ref);
            }
            if (i.getDebriefedAt() == null && i.getScheduledAt().plusMinutes(duration).isBefore(now)) {
                pending.add(ref);
            }
            if (i.getDebriefedAt() != null) {
                debriefed++;
                if (i.getSelfRating() != null) {
                    ratingSum += i.getSelfRating();
                    ratingCount++;
                    ratings.add(new RatingPoint(i.getId(), i.getScheduledAt(), i.getSelfRating(), ref.offerTitle()));
                }
            }
            if (!Text.isBlank(i.getToImprove())) {
                String text = i.getToImprove();
                for (String skill : dictionary.skillsIn(text)) {
                    skillTopics.computeIfAbsent(skill, k -> new LinkedHashSet<>()).add(i.getId());
                }
                String folded = Text.fold(text);
                Matcher m = WORD.matcher(folded);
                Set<String> seenHere = new LinkedHashSet<>();
                while (m.find()) {
                    String w = m.group();
                    if (w.length() < 5 || GENERIC.contains(w) || !dictionary.skillsIn(w).isEmpty()) {
                        continue;
                    }
                    String stem = Text.stem(w, TOPIC_STEM);
                    if (seenHere.add(stem)) {
                        wordTopics.computeIfAbsent(stem, k -> new LinkedHashSet<>()).add(i.getId());
                        topicLabels.putIfAbsent(stem, text.substring(m.start(), m.end()));
                    }
                }
            }
            for (String line : Text.lines(i.getQuestionsAsked())) {
                String q = BULLET.matcher(line).replaceFirst("").strip();
                if (q.length() < 3) {
                    continue;
                }
                asked.add(new AskedQuestion(q, i.getId(), i.getApplicationId(), ref.offerId(), ref.offerTitle(),
                        ref.company(), i.getScheduledAt(), List.copyOf(dictionary.skillsIn(q))));
            }
        }
        skillTopics.forEach((skill, ids) -> topics.put("s:" + skill,
                new Topic(skill, "SKILL", ids.size(), List.copyOf(ids))));
        wordTopics.forEach((stem, ids) -> {
            if (ids.size() >= 2) {
                topics.put("w:" + stem, new Topic(topicLabels.get(stem), "WORD", ids.size(), List.copyOf(ids)));
            }
        });
        List<Topic> sortedTopics = topics.values().stream()
                .sorted(Comparator.comparingInt(Topic::count).reversed()
                        .thenComparing(t -> "SKILL".equals(t.kind()) ? 0 : 1))
                .toList();
        pending.sort(Comparator.comparing(InterviewRef::scheduledAt).reversed());
        past.sort(Comparator.comparing(InterviewRef::scheduledAt).reversed());
        asked.sort(Comparator.comparing(AskedQuestion::date).reversed());

        List<String> notes = new ArrayList<>();
        if (!pending.isEmpty()) {
            notes.add("Masz " + pending.size() + " " + (pending.size() == 1 ? "rozmowę" : "rozmowy")
                    + " bez podsumowania — zapisz wnioski, póki pamiętasz szczegóły.");
        }
        sortedTopics.stream().filter(t -> t.count() >= 2).findFirst().ifPresent(t ->
                notes.add("Powtarza się temat „" + t.topic() + "” (" + t.count()
                        + " rozmowy) — warto zaplanować naukę lub przygotować odpowiedź."));
        Double avg = ratingCount == 0 ? null : Math.round(10.0 * ratingSum / ratingCount) / 10.0;
        return new Insights(upcoming, pending, past, debriefed, avg, ratings, sortedTopics, asked, notes);
    }

    private static InterviewRef ref(Interview i, JobApplication a) {
        return new InterviewRef(i.getId(), i.getApplicationId(), a == null ? null : a.getOffer().getId(),
                a == null ? null : a.getOffer().getTitle(), a == null ? null : a.getOffer().getCompany(),
                i.getScheduledAt(), i.getKind(), i.getDurationMinutes(), i.getLocation(), i.getInterviewers(),
                a == null ? null : a.getStatus(), i.getSelfRating(), i.getDebriefedAt());
    }

    private static String blankToNull(String s) {
        return Text.isBlank(s) ? null : s.strip();
    }
}

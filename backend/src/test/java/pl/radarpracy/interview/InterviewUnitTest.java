package pl.radarpracy.interview;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.skills.SkillDictionary;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Generator pytań (#53), bank historii STAR (#56) i wnioski z debriefów (#62). */
class InterviewUnitTest {

    private final SkillDictionary dictionary = new SkillDictionary();
    private final MatchService matching = new MatchService(dictionary, null, null);

    static final String CV = """
            Java Developer — PayFlow (2022 – obecnie)
            - Mikroserwisy w Spring Boot i PostgreSQL, Apache Kafka.
            - Skróciłam czas przetwarzania płatności o 40%.
            Angielski — B2
            """;

    private JobOffer offer() {
        JobOffer o = new JobOffer();
        o.setId(1L);
        o.setTitle("Senior Java Developer");
        o.setCompany("FinPay");
        o.setDescription("O projekcie:\nBudujemy platformę płatności natychmiastowych dla banków.\n\nWymagania:\n- Kubernetes");
        o.setRequirements(List.of(
                new Requirement("Minimum 5 lat doświadczenia w Java", true, 5),
                Requirement.must("Spring Boot i mikroserwisy"),
                Requirement.must("Kubernetes"),
                Requirement.must("Komunikatywność i praca w zespole"),
                Requirement.nice("Doświadczenie w domenie płatności"),
                Requirement.nice("Terraform")));
        return o;
    }

    private MatchResult match(JobOffer o) {
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new pl.radarpracy.profile.SkillEntry("Java", 4, 3)));
        return matching.matchRequirements(o.getRequirements(), CV, p);
    }

    @Test
    void gapsComeFirstThenTechnicalBehavioralMotivationalCompany() {
        InterviewQuestionService service = new InterviewQuestionService(new InterviewQuestionBank(), dictionary, null, null);
        JobOffer o = offer();
        List<InterviewQuestion> qs = service.generate(o, match(o));

        assertThat(qs.get(0).category()).isEqualTo(InterviewQuestion.GAP);
        assertThat(qs.get(0).skill()).isEqualTo("Kubernetes");
        assertThat(qs.get(0).why()).contains("„Kubernetes”").contains("obowiązkowe");
        // wymaganie częściowe (za mało lat Javy) też jest luką
        assertThat(qs).filteredOn(q -> q.category().equals(InterviewQuestion.GAP))
                .anyMatch(q -> q.requirement() != null && q.requirement().startsWith("Minimum 5 lat"));
        assertThat(qs).filteredOn(q -> q.category().equals(InterviewQuestion.GAP))
                .anyMatch(q -> "Terraform".equals(q.skill()));

        List<String> order = qs.stream().map(InterviewQuestion::category).distinct().toList();
        assertThat(order).containsExactly(InterviewQuestion.GAP, InterviewQuestion.TECHNICAL,
                InterviewQuestion.BEHAVIORAL, InterviewQuestion.MOTIVATIONAL, InterviewQuestion.COMPANY);

        InterviewQuestion spring = qs.stream().filter(q -> "Spring Boot".equals(q.skill())).findFirst().orElseThrow();
        assertThat(spring.category()).isEqualTo(InterviewQuestion.TECHNICAL);
        assertThat(spring.question()).contains("@Transactional");
        assertThat(CV).contains(spring.evidence());
        assertThat(spring.tip()).contains(spring.evidence());

        assertThat(qs).anyMatch(q -> q.category().equals(InterviewQuestion.BEHAVIORAL)
                && "Komunikatywność".equals(q.skill()));
        assertThat(qs).anyMatch(q -> q.category().equals(InterviewQuestion.MOTIVATIONAL)
                && q.question().contains("FinPay"));
        InterviewQuestion company = qs.stream().filter(q -> q.category().equals(InterviewQuestion.COMPANY))
                .findFirst().orElseThrow();
        assertThat(company.why()).contains("Budujemy platformę płatności natychmiastowych dla banków");
        assertThat(qs).anyMatch(q -> q.category().equals(InterviewQuestion.COMPANY)
                && q.requirement() != null && q.requirement().contains("domenie płatności"));
        assertThat(qs.stream().map(InterviewQuestion::question).distinct().count()).isEqualTo(qs.size());
    }

    @Test
    void generatorIsDeterministic() {
        InterviewQuestionService service = new InterviewQuestionService(new InterviewQuestionBank(), dictionary, null, null);
        JobOffer o = offer();
        assertThat(service.generate(o, match(o))).isEqualTo(service.generate(o, match(o)));
    }

    @Test
    void projectQuoteIsVerbatimAndSkipsBullets() {
        String desc = "Wymagania:\n- Rozwój systemu TMS\n\nTworzymy system dla sieci przychodni: rejestracja pacjentów.";
        String quote = InterviewQuestionService.projectQuote(desc);
        assertThat(quote).startsWith("Tworzymy system dla sieci przychodni");
        assertThat(desc).contains(quote);
        assertThat(InterviewQuestionService.projectQuote(null)).isNull();
    }

    private static StarStory story(long id, String title, String action, List<String> skills) {
        StarStory s = new StarStory();
        s.setId(id);
        s.setTitle(title);
        s.setAction(action);
        s.setSkills(skills);
        return s;
    }

    @Test
    void storiesAreSuggestedBySharedSkills() {
        StarStoryService service = new StarStoryService(null, dictionary, null);
        StarStory kafka = story(1, "Migracja na Kafkę", "Zaprojektowałam migrację na Apache Kafka w Spring Boot.", List.of());
        StarStory mentoring = story(2, "Mentoring juniorów", "Prowadziłam mentoring dwóch osób.", List.of("komunikatywność"));
        StarStory unrelated = story(3, "Raport w Excelu", "Przygotowałam raport.", List.of("Excel"));
        StarStoryService.Suggestions s = service.suggest(offer(), List.of(unrelated, mentoring, kafka));

        assertThat(s.suggestions()).extracting(x -> x.story().id()).containsExactly(1L, 2L);
        StarStoryService.StorySuggestion first = s.suggestions().get(0);
        // Spring Boot ⇒ Java (relacja „obejmuje”): historia pasuje do wymagań o Javie i Spring Boot
        assertThat(first.matchedSkills()).contains("Spring Boot", "Java");
        assertThat(first.score()).isEqualTo(6);
        assertThat(s.suggestions().get(1).matchedSkills()).contains("Komunikatywność");
        assertThat(s.uncoveredSkills()).contains("Kubernetes").doesNotContain("Java");
        assertThat(service.effectiveSkills(mentoring)).contains("Zarządzanie zespołem", "Komunikatywność");
    }

    @Test
    void insightsFindPendingDebriefsRecurringTopicsAndAskedQuestions() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 31, 12, 0);
        JobOffer o = offer();
        JobApplication app = new JobApplication();
        app.setId(50L);
        app.setOffer(o);
        app.setStatus(ApplicationStatus.INTERVIEW);
        ApplicationService apps = mock(ApplicationService.class);
        when(apps.all()).thenReturn(List.of(app));
        InterviewDebriefService service = new InterviewDebriefService(null, apps, dictionary);

        Interview done1 = interview(1L, now.minusDays(30), 3, "AWS — znam tylko S3, trzeba nadrobić IAM.",
                "- Jak działa @Transactional?\n2. Jakie usługi AWS znasz?");
        Interview done2 = interview(2L, now.minusDays(10), 4, "Oczekiwania finansowe i AWS w praktyce.", null);
        Interview pending = interview(3L, now.minusDays(1), null, null, null);
        pending.setDebriefedAt(null);
        Interview future = interview(4L, now.plusDays(3), null, null, null);
        future.setDebriefedAt(null);

        InterviewDebriefService.Insights in = service.insights(List.of(done1, done2, pending, future), now);
        assertThat(in.upcoming()).extracting(InterviewDebriefService.InterviewRef::interviewId).containsExactly(4L);
        assertThat(in.upcoming().get(0).offerTitle()).isEqualTo("Senior Java Developer");
        assertThat(in.pendingDebriefs()).extracting(InterviewDebriefService.InterviewRef::interviewId).containsExactly(3L);
        assertThat(in.past()).extracting(InterviewDebriefService.InterviewRef::interviewId).containsExactly(3L, 2L, 1L);
        assertThat(in.debriefedCount()).isEqualTo(2);
        assertThat(in.averageSelfRating()).isEqualTo(3.5);
        assertThat(in.recurringTopics().get(0).topic()).isEqualTo("AWS");
        assertThat(in.recurringTopics().get(0).count()).isEqualTo(2);
        assertThat(in.askedQuestions()).extracting(InterviewDebriefService.AskedQuestion::question)
                .containsExactly("Jak działa @Transactional?", "Jakie usługi AWS znasz?");
        assertThat(in.askedQuestions().get(1).skills()).contains("AWS");
        assertThat(in.notes()).anyMatch(n -> n.contains("bez podsumowania")).anyMatch(n -> n.contains("AWS"));
    }

    private static Interview interview(Long id, LocalDateTime at, Integer rating, String toImprove, String asked) {
        Interview i = new Interview();
        i.setId(id);
        i.setApplicationId(50L);
        i.setScheduledAt(at);
        i.setSelfRating(rating);
        i.setToImprove(toImprove);
        i.setQuestionsAsked(asked);
        i.setDebriefedAt(Instant.now());
        return i;
    }
}

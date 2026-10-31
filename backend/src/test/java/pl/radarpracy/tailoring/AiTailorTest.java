package pl.radarpracy.tailoring;

import org.junit.jupiter.api.Test;
import pl.radarpracy.ai.AiProperties;
import pl.radarpracy.ai.ClaudeClient;
import pl.radarpracy.ai.LlmUnavailableException;
import pl.radarpracy.ai.PiiMasker;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiTailorTest {

    private static final SkillDictionary DICT = new SkillDictionary();
    private final TruthGuard guard = new TruthGuard(DICT);
    private final CvTailor tailor = new CvTailor(DICT, guard);
    private final CandidateProfile profile = Demo.profile();

    /** Model na niby: zapamiętuje prompt i zwraca przygotowaną odpowiedź (albo rzuca wyjątek). */
    static class FakeClaude extends ClaudeClient {
        final Object response;
        String system;
        String user;

        FakeClaude(Object response) {
            super(new AiProperties(false, null, 0));
            this.response = response;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public <T> T completeStructured(String system, String user, Class<T> type) throws LlmUnavailableException {
            this.system = system;
            this.user = user;
            if (response instanceof LlmUnavailableException e) {
                throw e;
            }
            return type.cast(response);
        }
    }

    private JobOffer offer() {
        JobOffer o = new JobOffer();
        o.setTitle("Senior Java Developer");
        o.setCompany("FinPay Systems");
        o.setRequirements(Demo.requirements("OFFER_FINPAY"));
        return o;
    }

    private TailorDraft draft() {
        return tailor.tailor(Demo.cvBackend(), offer().getRequirements(), profile, TailoringOptions.defaults());
    }

    private static String idOf(TailorDraft d, String fragment) {
        return d.bullets().stream().filter(b -> b.content().contains(fragment)).findFirst().orElseThrow().id();
    }

    @Test
    void acceptedRewritesStayAndRejectedSentencesFallBackToOffline() {
        TailorDraft offline = draft();
        String kafka = idOf(offline, "Apache Kafka");
        String junit = idOf(offline, "JUnit 5");
        String good = "Skróciłam czas przetwarzania płatności o 40% dzięki migracji komunikacji między usługami na Apache Kafka.";
        String invented = "Wdrożyłam Kubernetes w 30 zespołach i pokryłam testami 100% kodu.";
        FakeClaude fake = new FakeClaude(new AiTailor.AiRewrite(
                List.of("Ekspertka Kubernetes z 10-letnim doświadczeniem."),
                List.of(new AiTailor.AiBullet(kafka, "- " + good), new AiTailor.AiBullet(junit, invented),
                        new AiTailor.AiBullet("r9b9", "Nieznany punkt"))));
        AiTailor ai = new AiTailor(fake, new PiiMasker(), guard);

        AiTailor.Result r = ai.improve(offline, offer(), profile, guard.facts(Demo.cvBackend(), profile));

        assertThat(r.source()).isEqualTo("ai");
        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.rejected()).hasSize(2);
        assertThat(r.rejected()).extracting(AiTailor.Rejection::proposed)
                .containsExactly("Ekspertka Kubernetes z 10-letnim doświadczeniem.", invented);
        assertThat(r.rejected().get(1).reason()).contains("Kubernetes");
        String text = r.draft().text();
        assertThat(text).contains("- " + good)
                .contains("- Wdrożenie testów JUnit 5 i Mockito — pokrycie wzrosło z 45% do 80%.")
                .doesNotContain("Kubernetes").doesNotContain("Nieznany");
        // podsumowanie wróciło do wersji offline
        assertThat(r.draft().summaryLines()).isEqualTo(offline.summaryLines());
        // całość nadal przechodzi strażnika
        assertThat(guard.check(Demo.cvBackend(), profile, text).passed()).isTrue();
    }

    @Test
    void promptContainsMaskedDataInTagsAndRules() {
        TailorDraft offline = draft();
        FakeClaude fake = new FakeClaude(new AiTailor.AiRewrite(List.of(), List.of()));
        CandidateProfile p = Demo.profile();
        // dane osobowe w treści punktu muszą zostać zamaskowane
        String cv = Demo.cvBackend().replace("Code review i mentoring dwóch juniorów.",
                "Code review i mentoring dwóch juniorów (kontakt: anna.nowak@example.com, Anna Nowak).");
        TailorDraft withPii = tailor.tailor(cv, offer().getRequirements(), p, TailoringOptions.defaults());
        AiTailor.Result r = new AiTailor(fake, new PiiMasker(), guard)
                .improve(withPii, offer(), p, guard.facts(cv, p));
        assertThat(fake.system).contains(ClaudeClient.DATA_RULES).contains("Nie dodawaj faktów");
        assertThat(fake.user).contains("<cv>").contains("</cv>").contains("<oferta>").contains("Apache Kafka")
                .doesNotContain("anna.nowak@example.com").doesNotContain("Anna Nowak").doesNotContain("+48 600");
        assertThat(r.source()).isEqualTo("offline");
        assertThat(r.draft().text()).isEqualTo(withPii.text());
        assertThat(offline.text()).isNotEqualTo(withPii.text());
    }

    @Test
    void unavailableModelMeansOfflineVersion() {
        TailorDraft offline = draft();
        AiTailor offlineAi = new AiTailor(new ClaudeClient(new AiProperties(true, null, 0)), new PiiMasker(), guard);
        AiTailor.Result r = offlineAi.improve(offline, offer(), profile, guard.facts(Demo.cvBackend(), profile));
        assertThat(r.source()).isEqualTo("offline");
        assertThat(r.note()).contains("AI niedostępne");
        assertThat(r.draft()).isSameAs(offline);

        AiTailor failing = new AiTailor(new FakeClaude(new LlmUnavailableException("limit")), new PiiMasker(), guard);
        AiTailor.Result f = failing.improve(offline, offer(), profile, guard.facts(Demo.cvBackend(), profile));
        assertThat(f.source()).isEqualTo("offline");
        assertThat(f.note()).contains("limit");
        assertThat(f.draft().text()).isEqualTo(offline.text());
    }

    @Test
    void cleanStripsRepeatedIdsAndBullets() {
        assertThat(AiTailor.clean("[r0b1] - Tekst punktu ")).isEqualTo("Tekst punktu");
        assertThat(AiTailor.clean("• Tekst")).isEqualTo("Tekst");
        assertThat(AiTailor.clean(null)).isEmpty();
    }
}

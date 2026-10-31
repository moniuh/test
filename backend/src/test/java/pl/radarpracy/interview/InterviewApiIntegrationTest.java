package pl.radarpracy.interview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static String id(String json) {
        return json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
    }

    private String createOffer() throws Exception {
        String body = """
                {"title":"Backend Developer (test rozmów)","company":"Testowa","description":"Wymagania:\\n- Java\\n- Spring Boot\\n- Kubernetes\\n- Komunikatywność"}
                """;
        return id(mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void questionsForOfferStartWithGaps() throws Exception {
        String offerId = createOffer();
        mvc.perform(get("/api/offers/" + offerId + "/interview-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("luka"))
                .andExpect(jsonPath("$[0].skill").value("Kubernetes"))
                .andExpect(jsonPath("$[*].category", hasItem("techniczne")))
                .andExpect(jsonPath("$[*].category", hasItem("motywacyjne")));
        mvc.perform(get("/api/offers/999999/interview-questions")).andExpect(status().isNotFound());
    }

    @Test
    void practiceAnswerIsAssessedAndSaved() throws Exception {
        String offerId = createOffer();
        String body = """
                {"question":"Opowiedz o trudnym projekcie","offerId":OFFER_ID,
                 "answer":"W poprzedniej pracy mieliśmy problem z wolnym API. Moim zadaniem było skrócenie czasu odpowiedzi. Najpierw przeanalizowałam logi, potem wdrożyłam cache w Spring Boot. W efekcie czas odpowiedzi spadł o 60%."}
                """.replace("OFFER_ID", offerId);
        String json = mvc.perform(post("/api/interview-practice/assess").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", greaterThan(50)))
                .andExpect(jsonPath("$.star.action.detected").value(true))
                .andExpect(jsonPath("$.star.result.evidence").value("W efekcie czas odpowiedzi spadł o 60%."))
                .andExpect(jsonPath("$.hasNumbers").value(true))
                .andExpect(jsonPath("$.length").value("TOO_SHORT"))
                .andExpect(jsonPath("$.offerSkillsMentioned", hasItem("Spring Boot")))
                .andExpect(jsonPath("$.source").value("offline"))
                .andExpect(jsonPath("$.savedId", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String savedId = json.replaceAll("(?s).*\"savedId\":(\\d+).*", "$1");
        mvc.perform(get("/api/interview-practice")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(Integer.parseInt(savedId))));
        mvc.perform(delete("/api/interview-practice/" + savedId)).andExpect(status().isNoContent());
        mvc.perform(post("/api/interview-practice/assess").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Pytanie\",\"answer\":\"  \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void starStoriesCrudAndSuggestions() throws Exception {
        String offerId = createOffer();
        String json = mvc.perform(post("/api/star-stories").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Wdrożenie na Kubernetes","situation":"Ręczne wdrożenia trwały godzinami.",
                         "action":"Przeniosłam usługi na Kubernetes i Helm.","result":"Wdrożenie trwa 10 minut.",
                         "skills":["k8s"]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills[0]").value("Kubernetes"))
                .andReturn().getResponse().getContentAsString();
        String storyId = id(json);
        mvc.perform(get("/api/star-stories/suggest").param("offerId", offerId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[*].story.id", hasItem(Integer.parseInt(storyId))))
                .andExpect(jsonPath("$.suggestions[0].matchedSkills", hasItem("Kubernetes")));
        mvc.perform(put("/api/star-stories/" + storyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nowy tytuł\",\"result\":\"Szybciej\",\"skills\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Nowy tytuł"));
        mvc.perform(post("/api/star-stories").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/star-stories/" + storyId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/star-stories/" + storyId)).andExpect(status().isNotFound());
    }

    @Test
    void debriefUpdatesInterviewAndInsights() throws Exception {
        String offerId = createOffer();
        String appId = id(mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":" + offerId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String interviewId = id(mvc.perform(post("/api/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationId\":" + appId + ",\"scheduledAt\":\"2026-01-10T10:00:00\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/interview-insights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDebriefs[*].interviewId", hasItem(Integer.parseInt(interviewId))));
        mvc.perform(put("/api/interviews/" + interviewId + "/debrief").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wentWell\":\"Spokój\",\"toImprove\":\"Kubernetes\",\"selfRating\":7}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/interviews/" + interviewId + "/debrief").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"wentWell":"Spokój","toImprove":"Kubernetes w praktyce","selfRating":4,
                                 "questionsAsked":"- Jak działa Kubernetes?\\n- Dlaczego zmieniasz pracę?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfRating").value(4))
                .andExpect(jsonPath("$.debriefedAt", notNullValue()));
        mvc.perform(get("/api/interview-insights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.askedQuestions[*].question", hasItem("Jak działa Kubernetes?")))
                .andExpect(jsonPath("$.averageSelfRating", notNullValue()));
        mvc.perform(get("/api/applications/" + appId + "/events"))
                .andExpect(jsonPath("$[*].type", hasItem("INTERVIEW_DONE")));
        mvc.perform(put("/api/interviews/999999/debrief").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selfRating\":3}")).andExpect(status().isNotFound());
    }
}

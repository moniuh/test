package pl.radarpracy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void demoDataAndCoreEndpoints() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/api/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Anna Nowak"));
        mvc.perform(get("/api/cv")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/offers")).andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(10)));
        mvc.perform(get("/api/matches")).andExpect(status().isOk()).andExpect(jsonPath("$[0].score").exists());
        mvc.perform(get("/api/applications")).andExpect(status().isOk()).andExpect(jsonPath("$[0].offerTitle").exists());
        mvc.perform(get("/api/interviews")).andExpect(status().isOk());
        mvc.perform(get("/api/ai/status")).andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void createOfferExtractsRequirementsAndMatches() throws Exception {
        String body = """
                {"title":"Java Developer","company":"Test","description":"Wymagania:\\n- Java\\n- Spring Boot\\n- Kubernetes"}
                """;
        String json = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requirements", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        String id = json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/offers/" + id + "/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements[0].evidenceVerified").value(true))
                .andExpect(jsonPath("$.missingSkills[0]").value("Kubernetes"));
        mvc.perform(get("/api/offers/" + id + "/salary")).andExpect(jsonPath("$.present").value(false));
    }

    @Test
    void errorsHaveUniformShape() throws Exception {
        mvc.perform(get("/api/offers/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicationStatusChangeRecordsEvent() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("{\"offerId\":1}"))
                .andExpect(status().isCreated());
        String list = mvc.perform(get("/api/applications?offerId=1")).andReturn().getResponse().getContentAsString();
        String id = list.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/applications/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INTERVIEW\",\"note\":\"zaproszenie\"}"))
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.firstResponseAt").exists());
        mvc.perform(get("/api/applications/" + id + "/events")).andExpect(jsonPath("$", hasSize(2)));
    }
}

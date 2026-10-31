package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpointy narzędzi CV. Test jest transakcyjny — utworzone CV, oferty i zmiany profilu są wycofywane,
 * więc nie wpływają na inne testy korzystające ze wspólnej bazy z danymi demo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CvToolsApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private long createCv(String name, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("name", name, "content", content));
        String res = mvc.perform(post("/api/cv").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void importPdfAndDocxCreatesNewCvVersions() throws Exception {
        byte[] pdf = CvTestFiles.pdf(List.of("Jan Testowy", "jan.testowy@example.com", "DOSWIADCZENIE",
                "- Built REST API in Java and Spring Boot (2021 - 2024)"));
        mvc.perform(multipart("/api/cv/import")
                        .file(new MockMultipartFile("file", "CV_Jan_Testowy.pdf", "application/pdf", pdf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cv.id").exists())
                .andExpect(jsonPath("$.cv.name").value("CV Jan Testowy"))
                .andExpect(jsonPath("$.cv.origin").value("import PDF"))
                .andExpect(jsonPath("$.cv.content", containsString("Built REST API in Java and Spring Boot")))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.pages").value(1))
                .andExpect(jsonPath("$.words", greaterThan(10)));

        byte[] docx = CvTestFiles.docx(List.of("Anna Kowalska", "Doświadczenie zawodowe", "Wdrożyłam system płatności."));
        String res = mvc.perform(multipart("/api/cv/import")
                        .file(new MockMultipartFile("file", "cv.docx", "application/octet-stream", docx))
                        .param("name", "Moje CV z Worda").param("targetRole", "Backend"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cv.name").value("Moje CV z Worda"))
                .andExpect(jsonPath("$.cv.targetRole").value("Backend"))
                .andExpect(jsonPath("$.cv.origin").value("import DOCX"))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(res).get("cv").get("id").asLong();
        mvc.perform(get("/api/cv/" + id)).andExpect(jsonPath("$.content", containsString("Wdrożyłam system płatności.")));
    }

    @Test
    void importRejectsInvalidFilesWithJsonError() throws Exception {
        mvc.perform(multipart("/api/cv/import")
                        .file(new MockMultipartFile("file", "cv.txt", "text/plain", "Tekst CV".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message", containsString("PDF lub DOCX")));
        mvc.perform(multipart("/api/cv/import")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf",
                                CvTestFiles.docx(List.of("Tekst")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("nie jest dokumentem PDF")));
        mvc.perform(multipart("/api/cv/import")
                        .file(new MockMultipartFile("file", "skan.pdf", "application/pdf",
                                CvTestFiles.pdf(List.of(), true, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("skan")));
        mvc.perform(multipart("/api/cv/import")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Wybierz plik")));
    }

    @Test
    void skillSuggestionsCanBeAppliedToProfile() throws Exception {
        long id = createCv("Test umiejętności", """
                Doświadczenie
                - Terraform — 2 lata, moduły dla AWS
                - Zbudowałam API w Java
                """);
        String res = mvc.perform(get("/api/cv/" + id + "/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value(id))
                .andReturn().getResponse().getContentAsString();
        JsonNode suggestions = json.readTree(res).get("suggestions");
        JsonNode terraform = find(suggestions, "Terraform");
        assertThat(terraform.get("years").asInt()).isEqualTo(2);
        assertThat(terraform.get("evidence").asString()).contains("Terraform");
        // Java jest w profilu demo; jeśli inny test go zmienił, flaga i tak musi być spójna z profilem
        assertThat(find(suggestions, "Java").has("alreadyInProfile")).isTrue();

        boolean hadTerraform = terraform.get("alreadyInProfile").asBoolean();
        String apply = mvc.perform(post("/api/cv/" + id + "/skills/apply").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"names\":[\"terraform\",\"Kubernetes\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode applied = json.readTree(apply);
        if (!hadTerraform) {
            assertThat(applied.get("added").get(0).asString()).isEqualTo("Terraform");
        }
        assertThat(applied.get("skipped").toString()).contains("Kubernetes — nie występuje w tej wersji CV");
        mvc.perform(get("/api/profile")).andExpect(jsonPath("$.skills[*].name", hasItem("Terraform")));
        // drugie dodanie nie tworzy duplikatu
        mvc.perform(post("/api/cv/" + id + "/skills/apply").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"names\":[\"Terraform\"]}"))
                .andExpect(jsonPath("$.added", hasSize(0)))
                .andExpect(jsonPath("$.skipped[0]", containsString("już jest w profilu")));
        mvc.perform(post("/api/cv/" + id + "/skills/apply").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"names\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditAndSensitiveData() throws Exception {
        long id = createCv("Test audytu", CvAuditServiceTest.GOOD_CV + "\nPESEL: 44051401359\nStan cywilny: kawaler\n");
        mvc.perform(get("/api/cv/" + id + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value(id))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.checks", hasSize(13)))
                .andExpect(jsonPath("$.checks[0].code").value("contact_email"))
                .andExpect(jsonPath("$.checks[0].status").value("ok"));
        mvc.perform(get("/api/cv/" + id + "/sensitive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasSize(2)))
                .andExpect(jsonPath("$.findings[0].category").value("PESEL"))
                .andExpect(jsonPath("$.findings[0].severity").value("high"))
                .andExpect(jsonPath("$.findings[0].quote").value("PESEL: 44051401359"))
                .andExpect(jsonPath("$.findings[1].category").value("MARITAL_STATUS"));
        mvc.perform(get("/api/cv/999999/audit")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(get("/api/cv/999999/sensitive")).andExpect(status().isNotFound());
        mvc.perform(get("/api/cv/999999/skills")).andExpect(status().isNotFound());
    }

    @Test
    void tailoringForOffer() throws Exception {
        long withDocker = createCv("Z Dockerem", "Docker (3 lata)\n");
        mvc.perform(post("/api/cv/" + withDocker + "/skills/apply").contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"Docker\"]}")).andExpect(status().isOk());
        long cvId = createCv("Bez Dockera", "Doświadczenie\n- Zbudowałam API w Java i PostgreSQL.\n");
        String offer = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Backend Developer","company":"Test","requirements":[
                          {"text":"Docker","mustHave":true},
                          {"text":"Kubernetes","mustHave":true},
                          {"text":"Oracle DB","mustHave":false},
                          {"text":"Java","mustHave":true}]}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long offerId = json.readTree(offer).get("id").asLong();

        mvc.perform(get("/api/offers/" + offerId + "/tailoring").param("cvId", String.valueOf(cvId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value(cvId))
                .andExpect(jsonPath("$.offerTitle").value("Backend Developer"))
                .andExpect(jsonPath("$.fromProfile[0].skill").value("Docker"))
                .andExpect(jsonPath("$.fromProfile[0].cvLine", containsString("Docker")))
                .andExpect(jsonPath("$.related[0].skill").value("Oracle DB"))
                .andExpect(jsonPath("$.related[0].relatedSkill").value("PostgreSQL"))
                .andExpect(jsonPath("$.missing[0].skill").value("Kubernetes"))
                .andExpect(jsonPath("$.missing[0].action").value("LEARN"))
                .andExpect(result -> {
                    JsonNode r = json.readTree(result.getResponse().getContentAsString());
                    assertThat(r.get("projectedScore").asInt()).isGreaterThan(r.get("currentScore").asInt());
                });
        mvc.perform(get("/api/offers/999999/tailoring")).andExpect(status().isNotFound());
    }

    private static JsonNode find(JsonNode suggestions, String name) {
        for (JsonNode s : suggestions) {
            if (name.equals(s.get("name").asString())) {
                return s;
            }
        }
        throw new AssertionError("Brak sugestii " + name + " w " + suggestions);
    }
}

package pl.radarpracy.tailoring;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.ProfileService;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TailoringApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;
    @Autowired
    OfferService offers;
    @Autowired
    ProfileService profiles;

    private long medikosId() {
        return offers.all().stream().filter(o -> "Medikos".equals(o.getCompany())).findFirst().orElseThrow().getId();
    }

    private long backendCvId() {
        return profiles.cvVariants().stream().filter(c -> "Backend Java".equals(c.getName())).findFirst()
                .orElseThrow().getId();
    }

    private static String json(Map<String, Object> body) {
        return JSON.writeValueAsString(body);
    }

    private MvcResult postJson(String path, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(body))).andReturn();
    }

    @Test
    void previewSaveLibraryDocxAndDelete() throws Exception {
        long offerId = medikosId();
        long baseCvId = backendCvId();
        Long activeBefore = profiles.profile().getActiveCvId();

        // oferty do wyboru: NEW/SAVED, od najlepiej dopasowanej
        String options = mvc.perform(get("/api/tailoring/offers")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> scores = JsonPath.read(options, "$[*].score");
        assertThat(scores).isNotEmpty().isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        List<String> statuses = JsonPath.read(options, "$[*].status");
        assertThat(statuses).allMatch(s -> s.equals("NEW") || s.equals("SAVED"));

        // podgląd (AI wyłączone w testach → wersja offline z informacją)
        Map<String, Object> previewReq = new HashMap<>();
        previewReq.put("offerId", offerId);
        previewReq.put("baseCvId", baseCvId);
        previewReq.put("options", Map.of("hideIrrelevantBullets", false, "useAi", true));
        String preview = mvc.perform(post("/api/tailoring/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(json(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("offline"))
                .andExpect(jsonPath("$.aiAvailable").value(false))
                .andExpect(jsonPath("$.aiNote", containsString("AI niedostępne")))
                .andExpect(jsonPath("$.guard.passed").value(true))
                .andExpect(jsonPath("$.content", containsString("KLUCZOWE KOMPETENCJE POD TO STANOWISKO")))
                .andExpect(jsonPath("$.addedFromProfile", org.hamcrest.Matchers.hasItems("React", "TypeScript")))
                .andExpect(jsonPath("$.diff.length()", greaterThan(10)))
                .andReturn().getResponse().getContentAsString();
        int before = JsonPath.read(preview, "$.comparison.before");
        int after = JsonPath.read(preview, "$.comparison.after");
        assertThat(after).isGreaterThan(before);
        List<String> changes = JsonPath.read(preview, "$.comparison.requirements[*].change");
        assertThat(changes).contains("GAINED_EVIDENCE", "STILL_MISSING");
        String content = JsonPath.read(preview, "$.content");

        // zapis nowej wersji
        Map<String, Object> saveReq = new HashMap<>(previewReq);
        saveReq.put("content", content);
        saveReq.put("source", "offline");
        MvcResult saved = postJson("/api/tailoring", saveReq);
        assertThat(saved.getResponse().getStatus()).isEqualTo(201);
        String savedJson = saved.getResponse().getContentAsString();
        long cvId = ((Number) JsonPath.read(savedJson, "$.item.cvVariantId")).longValue();
        assertThat((Integer) JsonPath.read(savedJson, "$.item.offer.id")).isEqualTo((int) offerId);
        assertThat((Integer) JsonPath.read(savedJson, "$.item.scoreAfter")).isEqualTo(after);
        assertThat((Boolean) JsonPath.read(savedJson, "$.guard.passed")).isTrue();

        mvc.perform(get("/api/cv/" + cvId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.origin", startsWith("dopasowane")))
                .andExpect(jsonPath("$.content").value(content));

        // biblioteka: wpis z ofertą i aplikacjami na tę ofertę (demo: aplikacja z inną wersją CV)
        mvc.perform(get("/api/tailoring?offerId=" + offerId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cvVariantId").value(cvId))
                .andExpect(jsonPath("$[0].offer.title").value("Fullstack Developer (Java + React)"))
                .andExpect(jsonPath("$[0].applications[0].usesThisVersion").value(false))
                .andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(get("/api/tailoring")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cvVariantId == " + cvId + ")]").isNotEmpty());
        mvc.perform(get("/api/tailoring/" + cvId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(content))
                .andExpect(jsonPath("$.comparison.after").value(after))
                .andExpect(jsonPath("$.diff.length()", greaterThan(10)));

        // eksport DOCX
        MvcResult docx = mvc.perform(get("/api/tailoring/" + cvId + "/docx")).andExpect(status().isOk())
                .andExpect(content().contentType(TailoringController.DOCX))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("CV-medikos.docx")))
                .andReturn();
        byte[] bytes = docx.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 2)).isEqualTo("PK");

        // usunięcie: wersja nieaktywna i niewysłana → znika także z profilu
        mvc.perform(delete("/api/tailoring/" + cvId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cvDeleted").value(true));
        mvc.perform(get("/api/tailoring/" + cvId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/cv/" + cvId)).andExpect(status().isNotFound());
        assertThat(profiles.profile().getActiveCvId()).isEqualTo(activeBefore);
    }

    @Test
    void guardBlocksInventedContentUnlessExplicitlyAccepted() throws Exception {
        long offerId = medikosId();
        long baseCvId = backendCvId();
        String base = profiles.cv(baseCvId).getContent();
        String invented = base.replace("UMIEJĘTNOŚCI\n", "UMIEJĘTNOŚCI\nKubernetes, Terraform\n");

        Map<String, Object> req = new HashMap<>();
        req.put("offerId", offerId);
        req.put("baseCvId", baseCvId);
        req.put("content", invented);
        // kontrola przed zapisem (np. po ręcznej edycji w podglądzie)
        mvc.perform(post("/api/tailoring/check").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guard.passed").value(false))
                .andExpect(jsonPath("$.guard.violations[0].kind").value("NEW_SKILL"))
                .andExpect(jsonPath("$.comparison.before").isNumber())
                .andExpect(jsonPath("$.diff[?(@.kind == 'ADDED')]").isNotEmpty());
        req.put("edited", true);
        mvc.perform(post("/api/tailoring").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("guard_violations"))
                .andExpect(jsonPath("$.details[0]", containsString("linia")))
                .andExpect(jsonPath("$.report.passed").value(false))
                .andExpect(jsonPath("$.report.violations[?(@.kind == 'NEW_SKILL')]").isNotEmpty());

        req.put("acceptViolations", true);
        req.put("activate", true);
        String json = mvc.perform(post("/api/tailoring").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.acceptedViolations").value(true))
                .andExpect(jsonPath("$.item.edited").value(true))
                .andExpect(jsonPath("$.item.active").value(true))
                .andExpect(jsonPath("$.item.violations", greaterThan(0)))
                .andReturn().getResponse().getContentAsString();
        long cvId = ((Number) JsonPath.read(json, "$.item.cvVariantId")).longValue();

        // aktywnej wersji nie usuwamy z profilu — tylko z biblioteki
        mvc.perform(delete("/api/tailoring/" + cvId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cvDeleted").value(false))
                .andExpect(jsonPath("$.message", containsString("aktywna")));
        mvc.perform(get("/api/cv/" + cvId)).andExpect(status().isOk());

        // sprzątanie: przywróć aktywne CV bazowe i usuń wersję testową
        mvc.perform(post("/api/cv/" + baseCvId + "/activate")).andExpect(status().isOk());
        mvc.perform(delete("/api/cv/" + cvId)).andExpect(status().isNoContent());
    }

    @Test
    void saveWithoutContentGeneratesOfflineVersionAndValidationErrorsAreUniform() throws Exception {
        long offerId = medikosId();
        String json = mvc.perform(post("/api/tailoring").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":" + offerId + ",\"name\":\"Moja wersja pod Medikos\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.name").value("Moja wersja pod Medikos"))
                .andExpect(jsonPath("$.item.source").value("offline"))
                .andExpect(jsonPath("$.content", not(containsString("Kubernetes"))))
                .andReturn().getResponse().getContentAsString();
        long cvId = ((Number) JsonPath.read(json, "$.item.cvVariantId")).longValue();
        mvc.perform(delete("/api/tailoring/" + cvId)).andExpect(jsonPath("$.cvDeleted").value(true));

        mvc.perform(post("/api/tailoring/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(post("/api/tailoring/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tailoring/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/tailoring/999999/docx")).andExpect(status().isNotFound());
    }
}

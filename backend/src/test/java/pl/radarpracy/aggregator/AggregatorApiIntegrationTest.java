package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AggregatorApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return JSON.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void demoSourcesAreSeededAndFetchedAtStartup() throws Exception {
        JsonNode sources = json(mvc.perform(get("/api/aggregator/sources")).andExpect(status().isOk()));
        JsonNode feed = null;
        JsonNode careers = null;
        JsonNode example = null;
        for (JsonNode s : sources) {
            String name = s.get("name").asString();
            if (name.equals(AggregatorDemoSeeder.DEMO_FEED_NAME)) {
                feed = s;
            } else if (name.equals(AggregatorDemoSeeder.DEMO_CAREERS_NAME)) {
                careers = s;
            } else if (name.startsWith("Przykład:")) {
                example = s;
            }
        }
        assertThat(feed).isNotNull();
        assertThat(feed.get("lastStatus").asString()).isEqualTo("OK");
        assertThat(feed.get("offersCount").asLong()).isGreaterThanOrEqualTo(6);
        assertThat(feed.get("lastRun").get("filteredOut").asInt()).isEqualTo(1);
        assertThat(careers).isNotNull();
        assertThat(careers.get("offersCount").asLong()).isEqualTo(3);
        assertThat(example).isNotNull();
        assertThat(example.get("enabled").asBoolean()).isFalse();
        assertThat(example.get("usesNetwork").asBoolean()).isTrue();
        assertThat(example.get("type").asString()).isEqualTo("RSS");
    }

    @Test
    void sourceRunInboxSeenSearchFlow() throws Exception {
        // 1. źródło DEMO z filtrem
        JsonNode created = json(postJson("/api/aggregator/sources", """
                {"name":"Test MockMvc","type":"DEMO","url":"test-feed.xml","excludeKeywords":["praca zmianowa"]}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(true)));
        long sourceId = created.get("id").asLong();

        // 2. pierwszy przebieg: 5 pozycji, 3 nowe, 1 duplikat (ten sam URL), 1 odfiltrowana
        postJson("/api/aggregator/sources/" + sourceId + "/run", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.fetched").value(5))
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.filteredOut").value(1))
                .andExpect(jsonPath("$.message", containsString("praca zmianowa")));

        // 3. drugi przebieg: wszystko już jest
        postJson("/api/aggregator/sources/" + sourceId + "/run", "{}")
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.duplicates").value(4))
                .andExpect(jsonPath("$.filteredOut").value(1));

        // 4. skrzynka
        JsonNode inbox = json(mvc.perform(get("/api/aggregator/inbox").param("sourceId", String.valueOf(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.unseen", greaterThanOrEqualTo(3))));
        List<Long> ids = new ArrayList<>();
        Long injectedId = null;
        for (JsonNode item : inbox.get("items")) {
            ids.add(item.get("offerId").asLong());
            assertThat(item.get("seen").asBoolean()).isFalse();
            assertThat(item.get("origin").asString()).isEqualTo("SOURCE");
            assertThat(item.get("sourceName").asString()).isEqualTo("Test MockMvc");
            assertThat(item.get("score").isNumber()).isTrue();
            if (item.get("title").asString().startsWith("Analityk Fioletowy")) {
                injectedId = item.get("offerId").asLong();
                assertThat(item.get("signals").size()).isGreaterThan(0);
            }
            if (item.get("title").asString().startsWith("Inżynier Integracji")) {
                assertThat(item.get("salaryMin").asInt()).isEqualTo(18000);
                assertThat(item.get("company").asString()).isEqualTo("Firma Testowa Alfa");
            }
        }
        assertThat(injectedId).isNotNull();
        // posortowane wg dopasowania
        JsonNode items = inbox.get("items");
        assertThat(items.get(0).get("score").asInt()).isGreaterThanOrEqualTo(items.get(2).get("score").asInt());

        // 5. sygnały prompt injection i metadane
        mvc.perform(get("/api/aggregator/offers/" + injectedId + "/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("SOURCE"))
                .andExpect(jsonPath("$.signals[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.signals[0].quote", containsString("Ignore previous instructions")));
        mvc.perform(get("/api/aggregator/offers/" + injectedId))
                .andExpect(jsonPath("$.externalId").value("tf-3"))
                .andExpect(jsonPath("$.analysisNotes[0]").exists());
        mvc.perform(get("/api/aggregator/offers/1/signals")).andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.analyzedNow").value(true));

        // 6. przejrzane: jedna, potem wszystkie ze źródła
        postJson("/api/aggregator/inbox/seen", "{\"offerIds\":[" + ids.get(0) + "]}")
                .andExpect(jsonPath("$.marked").value(1));
        mvc.perform(get("/api/aggregator/inbox").param("sourceId", String.valueOf(sourceId)).param("onlyUnseen", "true"))
                .andExpect(jsonPath("$.items", hasSize(2)));
        postJson("/api/aggregator/inbox/seen", "{\"all\":true,\"sourceId\":" + sourceId + "}")
                .andExpect(jsonPath("$.marked").value(2));
        mvc.perform(get("/api/aggregator/inbox").param("sourceId", String.valueOf(sourceId)).param("onlyUnseen", "true"))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/aggregator/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sources", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.schedulerEnabled").value(false));

        // 7. wyszukiwarka
        mvc.perform(get("/api/aggregator/search").param("q", "zefirowa -bursztyn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].title").value("Inżynier Integracji Zefirowej"))
                .andExpect(jsonPath("$.results[0].sourceName").value("Test MockMvc"))
                .andExpect(jsonPath("$.results[0].snippet", containsString("zefirowe")))
                .andExpect(jsonPath("$.results[0].matchedFields[0]").value("tytuł"));
        mvc.perform(get("/api/aggregator/search").param("q", "\"tester bursztynowy\"").param("status", "NEW"))
                .andExpect(jsonPath("$.total").value(1));

        // 8. zapis oferty usuwa ją ze skrzynki (status SAVED)
        postJson("/api/offers/" + ids.get(0) + "/status", "{\"status\":\"SAVED\"}").andExpect(status().isOk());
        mvc.perform(get("/api/aggregator/inbox").param("sourceId", String.valueOf(sourceId)))
                .andExpect(jsonPath("$.items", hasSize(2)));

        // 9. historia, stan źródła, podgląd bez zapisu
        mvc.perform(get("/api/aggregator/runs").param("sourceId", String.valueOf(sourceId)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].trigger").value("MANUAL"));
        mvc.perform(get("/api/aggregator/sources/" + sourceId))
                .andExpect(jsonPath("$.offersCount").value(3))
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastRun.created").value(0));
        JsonNode preview = json(postJson("/api/aggregator/sources/test", """
                {"type":"DEMO","url":"test-feed.xml","excludeKeywords":["praca zmianowa"]}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items", hasSize(5))).andExpect(jsonPath("$.format").value("RSS")));
        assertThat(preview.get("items").get(0).get("duplicateOf").isNull()).isFalse();
        assertThat(preview.get("items").get(1).get("accepted").asBoolean()).isFalse();

        // 10. włącz/wyłącz, usuń
        postJson("/api/aggregator/sources/" + sourceId + "/enabled", "{\"enabled\":false}")
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(delete("/api/aggregator/sources/" + sourceId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/aggregator/sources/" + sourceId)).andExpect(status().isNotFound());
        // oferty zostają w skrzynce z nazwą źródła
        mvc.perform(get("/api/aggregator/offers/" + injectedId + "/signals"))
                .andExpect(jsonPath("$.sourceName").value("Test MockMvc"));
    }

    @Test
    void invalidSourcesAreRejected() throws Exception {
        postJson("/api/aggregator/sources", "{\"name\":\"Lokalny\",\"type\":\"RSS\",\"url\":\"http://127.0.0.1:8080/feed\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SSRF")));
        postJson("/api/aggregator/sources", "{\"name\":\"Metadane\",\"type\":\"JSONLD_PAGE\",\"url\":\"http://169.254.169.254/\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/aggregator/sources", "{\"name\":\"Plik\",\"type\":\"RSS\",\"url\":\"file:///etc/passwd\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("http")));
        postJson("/api/aggregator/sources", "{\"name\":\"Ścieżka\",\"type\":\"DEMO\",\"url\":\"../application.yml\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/aggregator/sources", "{\"type\":\"DEMO\",\"url\":\"demo-feed.xml\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        postJson("/api/aggregator/sources/test", "{\"type\":\"RSS\",\"url\":\"http://10.0.0.1/rss\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/aggregator/sources/999999/run", "{}").andExpect(status().isNotFound());
    }

    @Test
    void settingsAndArchiveEndpoints() throws Exception {
        mvc.perform(get("/api/aggregator/settings"))
                .andExpect(jsonPath("$.userAgent").value("RadarPracy-test"))
                .andExpect(jsonPath("$.demoFiles[0]").value("demo-feed.xml"));
        postJson("/api/aggregator/archive-expired", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/aggregator/search").param("q", "")).andExpect(jsonPath("$.total").value(0));
    }
}

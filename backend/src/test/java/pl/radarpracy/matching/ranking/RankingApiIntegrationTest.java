package pl.radarpracy.matching.ranking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.radarpracy.profile.ProfileService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RankingApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;

    @Autowired
    ProfileService profiles;

    private JsonNode getJson(String url) throws Exception {
        return JSON.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private long createOffer(String title, String description) throws Exception {
        String body = JSON.writeValueAsString(java.util.Map.of("title", title, "company", "Test Ranking",
                "description", description));
        String json = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(json).get("id").asLong();
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(n.get("offer").get("id").asLong()));
        return out;
    }

    @Test
    void rankingListsOffersWithComponentsSortedByRank() throws Exception {
        JsonNode list = getJson("/api/ranking");
        assertThat(list.size()).isPositive();
        int previous = Integer.MAX_VALUE;
        for (JsonNode n : list) {
            int rank = n.get("rankScore").asInt();
            assertThat(rank).isBetween(0, 100).isLessThanOrEqualTo(previous);
            previous = rank;
            assertThat(n.get("excluded").asBoolean()).isFalse();
            assertThat(n.get("components")).hasSize(RankingWeights.Factor.values().length);
            assertThat(n.get("components").get(0).get("key").asString()).isEqualTo("cv");
            assertThat(n.get("offer").get("title").asString()).isNotBlank();
        }
        mvc.perform(get("/api/ranking?sort=cena")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(get("/api/ranking?minScore=150")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ranking?status=NEW,SAVED&workModes=REMOTE&sort=salary")).andExpect(status().isOk());
    }

    @Test
    void dealBreakerOfferIsHiddenUnlessRequested() throws Exception {
        List<String> breakers = profiles.profile().getDealBreakers();
        assumeThat(breakers).isNotEmpty();
        long id = createOffer("Konsultant Rankingowy Qxv", "Wymagania:\n- " + breakers.get(0) + "\n- komunikatywność");

        assertThat(ids(getJson("/api/ranking?q=qxv"))).doesNotContain(id);
        JsonNode all = getJson("/api/ranking?q=qxv&includeExcluded=true");
        assertThat(ids(all)).containsExactly(id);
        JsonNode item = all.get(0);
        assertThat(item.get("excluded").asBoolean()).isTrue();
        assertThat(item.get("exclusions").get(0).get("type").asString()).isEqualTo("DEAL_BREAKER");
        assertThat(item.get("exclusions").get(0).get("evidence").asString()).isNotBlank();
    }

    @Test
    void preferencesEndpointExplainsLearningState() throws Exception {
        JsonNode p = getJson("/api/ranking/preferences");
        assertThat(p.get("minRatings").asInt()).isEqualTo(RankingWeights.LEARNING_MIN_RATINGS);
        assertThat(p.get("maxPoints").asDouble()).isEqualTo(10.0);
        assertThat(p.get("active").asBoolean()).isEqualTo(p.get("ratings").asInt() >= p.get("minRatings").asInt());
        assertThat(p.get("note").asString()).isNotBlank();
    }

    @Test
    void similarOffersSkipTheOfferItselfAndRespectLimit() throws Exception {
        long a = createOffer("Programista Zorblax Java", "Wymagania:\n- Java\n- Spring Boot\n- Kafka\n- zorblax");
        long b = createOffer("Inżynier Zorblax Java", "Wymagania:\n- Java\n- Spring Boot\n- Kafka\n- zorblax");
        JsonNode similar = getJson("/api/offers/" + a + "/similar?limit=3");
        assertThat(similar.size()).isBetween(1, 3);
        List<Long> similarIds = new ArrayList<>();
        similar.forEach(n -> similarIds.add(n.get("offerId").asLong()));
        assertThat(similarIds).doesNotContain(a).contains(b);
        JsonNode first = similar.get(0);
        assertThat(first.get("similarity").asDouble()).isBetween(0.0, 1.0);
        assertThat(first.get("sharedSkills").isArray()).isTrue();
        mvc.perform(get("/api/offers/99999999/similar")).andExpect(status().isNotFound());
    }

    @Test
    void savedSearchLifecycleCountsNewOffers() throws Exception {
        String body = """
                {"name":"Wombatix zdalnie","query":"wombatix","statuses":["NEW","SAVED"],"minScore":0}
                """;
        String created = mvc.perform(post("/api/saved-searches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.newCount").value(0))
                .andExpect(jsonPath("$.lastCheckedAt").exists())
                .andReturn().getResponse().getContentAsString();
        long id = JSON.readTree(created).get("id").asLong();

        long offerId = createOffer("Specjalista Wombatix", "Szukamy osoby do projektu Wombatix.");

        JsonNode listed = null;
        for (JsonNode s : getJson("/api/saved-searches")) {
            if (s.get("id").asLong() == id) {
                listed = s;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("totalCount").asInt()).isEqualTo(1);
        assertThat(listed.get("newCount").asInt()).isEqualTo(1);

        mvc.perform(get("/api/saved-searches/" + id + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].offer.id").value(offerId))
                .andExpect(jsonPath("$.newOfferIds[0]").value(offerId));

        mvc.perform(post("/api/saved-searches/" + id + "/mark-seen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCount").value(0))
                .andExpect(jsonPath("$.totalCount").value(1));

        mvc.perform(put("/api/saved-searches/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wombatix — archiwum\",\"query\":\"wombatix\",\"statuses\":[\"ARCHIVED\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wombatix — archiwum"))
                .andExpect(jsonPath("$.totalCount").value(0));

        mvc.perform(delete("/api/saved-searches/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/saved-searches/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void savedSearchValidation() throws Exception {
        mvc.perform(post("/api/saved-searches").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(post("/api/saved-searches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"minScore\":120}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/saved-searches/99999999/mark-seen")).andExpect(status().isNotFound());
    }
}

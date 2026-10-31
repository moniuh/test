package pl.radarpracy.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpointy modułu analityki. Kontekst Springa jest współdzielony z innymi testami, więc tworzymy własne
 * oferty i aplikacje i sprawdzamy je po identyfikatorach/nazwach, a nie po dokładnych licznościach.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsApiIntegrationTest {

    private static final String COMPANY = "Analityka Test sp. z o.o.";
    private static final String SOURCE = "testowe źródło";

    @Autowired
    MockMvc mvc;

    private static String id(String json) {
        return json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
    }

    /** Oferta (Java + Kubernetes) i wysłana dziś aplikacja z follow-upem zaległym od 2 dni. */
    private long createApplicationWithOverdueFollowUp() throws Exception {
        String offer = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Analityk danych\",\"company\":\"" + COMPANY + "\",\"source\":\"" + SOURCE
                                + "\",\"description\":\"Wymagania:\\n- Java\\n- Kubernetes\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String body = "{\"offerId\":" + id(offer) + ",\"status\":\"APPLIED\",\"nextFollowUpAt\":\""
                + LocalDate.now().minusDays(2) + "\"}";
        String app = mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(id(app));
    }

    @Test
    void funnelAndEffectiveness() throws Exception {
        createApplicationWithOverdueFollowUp();
        mvc.perform(get("/api/analytics/funnel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stages", hasSize(5)))
                .andExpect(jsonPath("$.stages[0].key").value("SENT"))
                .andExpect(jsonPath("$.stages[0].count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.stages[0].conversionFromStart").value(100.0))
                .andExpect(jsonPath("$.plannedExcluded").isNumber())
                .andExpect(jsonPath("$.summary").isString());
        LocalDate today = LocalDate.now();
        mvc.perform(get("/api/analytics/funnel?from=" + today + "&to=" + today)).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.toString()))
                .andExpect(jsonPath("$.stages[0].count", greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/analytics/funnel?from=2026-10-10&to=2026-10-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(get("/api/analytics/funnel?from=nie-data")).andExpect(status().isBadRequest());

        mvc.perform(get("/api/analytics/effectiveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.minSample").value(5))
                .andExpect(jsonPath("$.bySource[*].name", hasItem(SOURCE)))
                .andExpect(jsonPath("$.byCv.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.bySource[0].smallSample").isBoolean())
                .andExpect(jsonPath("$.sourceInsight.text").isString());
    }

    @Test
    void dashboardAndNextActions() throws Exception {
        long appId = createApplicationWithOverdueFollowUp();
        mvc.perform(get("/api/analytics/dashboard")).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateName").isString())
                .andExpect(jsonPath("$.week.goal").isNumber())
                .andExpect(jsonPath("$.week.applications", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recentWeeks", hasSize(8)))
                .andExpect(jsonPath("$.upcomingInterviews").isArray())
                .andExpect(jsonPath("$.overdueFollowUps[*].applicationId", hasItem((int) appId)))
                .andExpect(jsonPath("$.topOffers").isArray())
                .andExpect(jsonPath("$.counters.openApplications", greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/analytics/next-actions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].link", hasItem("/aplikacje/" + appId)))
                .andExpect(jsonPath("$[*].kind", hasItem("FOLLOW_UP")))
                .andExpect(jsonPath("$[0].priority").isNumber())
                .andExpect(jsonPath("$[0].level").isString())
                .andExpect(jsonPath("$[0].link", startsWith("/")));
    }

    @Test
    void skillGapsAndTrends() throws Exception {
        mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Platform Engineer\",\"company\":\"" + COMPANY
                                + "\",\"description\":\"Wymagania:\\n- Java\\n- Kubernetes\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/analytics/skill-gaps")).andExpect(status().isOk())
                .andExpect(jsonPath("$.cvName").isString())
                .andExpect(jsonPath("$.threshold").value(70))
                .andExpect(jsonPath("$.offersAnalyzed", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.gaps[*].skill", hasItem("Kubernetes")))
                .andExpect(jsonPath("$.gaps[0].unlockedOffers").isNumber())
                .andExpect(jsonPath("$.note").isString());
        mvc.perform(get("/api/analytics/skill-gaps?cvId=999999")).andExpect(status().isNotFound());

        mvc.perform(get("/api/analytics/skill-trends?days=30")).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.skills[*].skill", hasItem("Kubernetes")))
                .andExpect(jsonPath("$.skills[0].candidateHas").isBoolean());
        mvc.perform(get("/api/analytics/skill-trends?days=3")).andExpect(status().isBadRequest());
    }

    @Test
    void moodEntryIsUpsertedPerDayAndValidated() throws Exception {
        LocalDate day = LocalDate.now().minusDays(400); // poza oknem reguł ostrzeżeń i historią innych testów
        String first = "{\"date\":\"" + day + "\",\"mood\":2,\"energy\":3,\"note\":\"pierwszy\"}";
        String second = "{\"date\":\"" + day + "\",\"mood\":4,\"energy\":5,\"note\":\"poprawiony\"}";
        String id1 = id(mvc.perform(post("/api/mood").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.mood").value(2))
                .andExpect(jsonPath("$.entry.date").value(day.toString()))
                .andReturn().getResponse().getContentAsString());
        String id2 = id(mvc.perform(post("/api/mood").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.mood").value(4))
                .andExpect(jsonPath("$.entry.energy").value(5))
                .andExpect(jsonPath("$.entry.note").value("poprawiony"))
                .andReturn().getResponse().getContentAsString());
        assertThat(id2).isEqualTo(id1);

        mvc.perform(get("/api/mood")).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.entries").isArray());

        mvc.perform(post("/api/mood").contentType(MediaType.APPLICATION_JSON).content("{\"mood\":6,\"energy\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("1–5")));
        String future = "{\"date\":\"" + LocalDate.now().plusDays(1) + "\",\"mood\":3,\"energy\":3}";
        mvc.perform(post("/api/mood").contentType(MediaType.APPLICATION_JSON).content(future))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/mood?days=0")).andExpect(status().isBadRequest());
    }

    @Test
    void reportJsonAndCsv() throws Exception {
        createApplicationWithOverdueFollowUp();
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(60);
        mvc.perform(get("/api/analytics/report?from=" + from + "&to=" + to)).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateName").isString())
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.applications[*].company", hasItem(COMPANY)))
                .andExpect(jsonPath("$.applications[*].statusLabel", hasItem("Wysłana")))
                .andExpect(jsonPath("$.interviews").isArray())
                .andExpect(jsonPath("$.summary.applicationsSent", greaterThanOrEqualTo(1)));

        byte[] csv = mvc.perform(get("/api/analytics/report.csv?from=" + from + "&to=" + to))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("Data;Firma;Stanowisko;Źródło;Status")
                .contains(COMPANY + ";Analityk danych;" + SOURCE + ";Wysłana");

        mvc.perform(get("/api/analytics/report?from=2026-10-10&to=2026-10-01")).andExpect(status().isBadRequest());
    }
}

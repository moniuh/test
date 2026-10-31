package pl.radarpracy.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpointy modułu „Aplikowanie i śledzenie” (pomysły #39–#47, #52). Każdy test tworzy własne oferty. */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationModuleIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;

    private static final String REQS = "Wymagania:\\n- Java\\n- Spring Boot\\n- Apache Kafka\\n- Kubernetes";

    private long offer(String title, String company) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"company\":\"" + company + "\",\"description\":\"" + REQS + "\"}";
        return id(mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    }

    private long application(long offerId, String status, String extra) throws Exception {
        String body = "{\"offerId\":" + offerId + ",\"status\":\"" + status + "\"" + (extra == null ? "" : "," + extra) + "}";
        return id(mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));
    }

    private static long id(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int days() {
        return FollowUpPolicy.days(profiles.profile().getFollowUpDays());
    }

    @Test
    void appliedApplicationGetsFollowUpSnapshotAndCoverLetter() throws Exception {
        long offerId = offer("Kotlin Developer", "Snapshot Test sp. z o.o.");
        long id = application(offerId, "APPLIED", "\"coverLetter\":\"Mój list\"");
        String today = LocalDate.now().toString();

        mvc.perform(get("/api/applications/" + id))
                .andExpect(jsonPath("$.appliedAt").value(today))
                .andExpect(jsonPath("$.nextFollowUpAt").value(LocalDate.now().plusDays(days()).toString()))
                .andExpect(jsonPath("$.cvSnapshot").value(notNullValue()));
        mvc.perform(get("/api/applications/" + id + "/snapshot"))
                .andExpect(jsonPath("$.hasSnapshot").value(true))
                .andExpect(jsonPath("$.cvChanged").value(false))
                .andExpect(jsonPath("$.coverLetterSnapshot").value("Mój list"))
                .andExpect(jsonPath("$.coverLetterChanged").value(false));

        mvc.perform(put("/api/applications/" + id + "/cover-letter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coverLetter\":\"Nowy list\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coverLetter").value("Nowy list"));
        mvc.perform(get("/api/applications/" + id + "/snapshot"))
                .andExpect(jsonPath("$.coverLetterSnapshot").value("Mój list"))
                .andExpect(jsonPath("$.coverLetterChanged").value(true));
        // snapshot nie jest nadpisywany
        mvc.perform(post("/api/applications/" + id + "/snapshot")).andExpect(status().isBadRequest());
    }

    @Test
    void plannedApplicationHasNoSnapshotUntilSent() throws Exception {
        long offerId = offer("QA Engineer", "Planowana Firma");
        long id = application(offerId, "PLANNED", null);
        mvc.perform(get("/api/applications/" + id))
                .andExpect(jsonPath("$.nextFollowUpAt").value(nullValue()))
                .andExpect(jsonPath("$.cvSnapshot").value(nullValue()));
        mvc.perform(get("/api/applications/" + id + "/snapshot")).andExpect(jsonPath("$.hasSnapshot").value(false));
        postJson("/api/applications/" + id + "/status", "{\"status\":\"APPLIED\"}")
                .andExpect(jsonPath("$.cvSnapshot").value(notNullValue()))
                .andExpect(jsonPath("$.nextFollowUpAt").value(LocalDate.now().plusDays(days()).toString()));
    }

    @Test
    void moveChangesStatusAndOrderAndRecordsEvent() throws Exception {
        long a = application(offer("Go Developer", "Tablica A"), "PLANNED", null);
        long c = application(offer("Go Developer", "Tablica C"), "APPLIED", null);

        postJson("/api/applications/" + a + "/move", "{\"status\":\"APPLIED\",\"position\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.boardPosition").value(0))
                .andExpect(jsonPath("$.appliedAt").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.nextFollowUpAt").value(notNullValue()))
                .andExpect(jsonPath("$.cvSnapshot").value(notNullValue()));
        mvc.perform(get("/api/applications/" + c)).andExpect(jsonPath("$.boardPosition", greaterThan(0)));
        mvc.perform(get("/api/applications/" + a + "/events"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].fromStatus").value("PLANNED"))
                .andExpect(jsonPath("$[1].toStatus").value("APPLIED"));

        // przesunięcie w obrębie kolumny: bez nowego zdarzenia, pozycja przycięta do końca kolumny
        postJson("/api/applications/" + a + "/move", "{\"status\":\"APPLIED\",\"position\":9999}")
                .andExpect(jsonPath("$.boardPosition", greaterThan(0)));
        mvc.perform(get("/api/applications/" + a + "/events")).andExpect(jsonPath("$", hasSize(2)));
        // kolumna ma ciągłą numerację
        String list = mvc.perform(get("/api/applications")).andReturn().getResponse().getContentAsString();
        List<Integer> positions = JsonPath.read(list, "$[?(@.status == 'APPLIED')].boardPosition");
        assertThat(positions).doesNotHaveDuplicates();
        assertThat(positions.stream().mapToInt(Integer::intValue).max().orElse(0)).isEqualTo(positions.size() - 1);

        postJson("/api/applications/" + a + "/move", "{\"position\":1}").andExpect(status().isBadRequest());
    }

    @Test
    void followUpListSendAndSnooze() throws Exception {
        long id = application(offer("Scala Developer", "Follow Up Firma"), "APPLIED", null);
        LocalDate today = LocalDate.now();
        mvc.perform(put("/api/applications/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextFollowUpAt\":\"" + today.minusDays(3) + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/applications/follow-ups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(today.toString()))
                .andExpect(jsonPath("$.overdue[?(@.applicationId == " + id + ")].days").value(hasItem(-3)))
                .andExpect(jsonPath("$.overdue[?(@.applicationId == " + id + ")].company").value(hasItem("Follow Up Firma")));

        postJson("/api/applications/" + id + "/follow-up", "{\"note\":\"Mail do rekrutera\"}")
                .andExpect(jsonPath("$.lastContactAt").value(today.toString()))
                .andExpect(jsonPath("$.nextFollowUpAt").value(today.plusDays(days()).toString()));
        mvc.perform(get("/api/applications/" + id + "/events"))
                .andExpect(jsonPath("$[?(@.type == 'FOLLOW_UP_SENT')].note").value(hasItem("Mail do rekrutera")));

        postJson("/api/applications/" + id + "/snooze", "{\"days\":3}")
                .andExpect(jsonPath("$.nextFollowUpAt").value(today.plusDays(days() + 3L).toString()));
        postJson("/api/applications/" + id + "/snooze", "{\"days\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));

        // odpowiedź firmy czyści przypomnienie
        postJson("/api/applications/" + id + "/status", "{\"status\":\"SCREENING\"}")
                .andExpect(jsonPath("$.nextFollowUpAt").value(nullValue()));
        String list = mvc.perform(get("/api/applications/follow-ups?days=365")).andReturn().getResponse()
                .getContentAsString();
        List<Object> ids = JsonPath.read(list, "$..applicationId");
        assertThat(ids).doesNotContain((int) id);
    }

    @Test
    void timelineAndCalendarExport() throws Exception {
        long id = application(offer("Rust Developer", "Oś Czasu"), "APPLIED", "\"contactName\":\"Ewa Test\"");
        postJson("/api/applications/" + id + "/notes", "{\"note\":\"Notatka testowa\"}").andExpect(status().isCreated());
        postJson("/api/applications/" + id + "/notes", "{\"note\":\"   \"}").andExpect(status().isBadRequest());
        String when = LocalDateTime.now().plusDays(5).withHour(14).withMinute(0).withSecond(0).withNano(0).toString();
        long interviewId = id(postJson("/api/interviews", "{\"applicationId\":" + id + ",\"scheduledAt\":\"" + when
                + "\",\"kind\":\"TECHNICAL\",\"durationMinutes\":90,\"location\":\"Biuro; sala 2, piętro 3\","
                + "\"interviewers\":\"Tech Lead\",\"notes\":\"Przygotować pytania\"}").andExpect(status().isCreated()));

        mvc.perform(get("/api/applications/" + id + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("EVENT"))
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[?(@.kind == 'NOTE')].detail").value(hasItem("Notatka testowa")))
                .andExpect(jsonPath("$[-1].kind").value("INTERVIEW"))
                .andExpect(jsonPath("$[-1].interviewId").value(interviewId))
                .andExpect(jsonPath("$[-1].upcoming").value(true));

        mvc.perform(get("/api/interviews/" + interviewId + "/ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/calendar")))
                .andExpect(header().string("Content-Disposition", containsString("rozmowa-" + interviewId + ".ics")))
                .andExpect(content().string(startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n")))
                .andExpect(content().string(containsString("UID:interview-" + interviewId + "@radarpracy\r\n")))
                .andExpect(content().string(containsString("LOCATION:Biuro\\; sala 2\\, piętro 3\r\n")))
                .andExpect(content().string(containsString("Kontakt: Ewa Test")));
        mvc.perform(get("/api/interviews/calendar.ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/calendar")))
                .andExpect(content().string(containsString("UID:interview-" + interviewId + "@radarpracy")))
                .andExpect(content().string(containsString("X-WR-CALNAME:")));
        mvc.perform(get("/api/interviews/999999/ics")).andExpect(status().isNotFound());
        // rdzeniowe /api/interviews/{id} nadal działa
        mvc.perform(get("/api/interviews/" + interviewId)).andExpect(jsonPath("$.notes").value("Przygotować pytania"));
    }

    @Test
    void messageTemplatesRenderAndSave() throws Exception {
        long id = application(offer("Elixir Developer", "Wiadomości SA"), "APPLIED",
                "\"contactName\":\"Jan Rekruter (HR)\",\"contactEmail\":\"hr@wiadomosci.example\"");
        mvc.perform(get("/api/message-templates"))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].id").value("follow-up"));

        postJson("/api/applications/" + id + "/messages", "{\"templateId\":\"follow-up\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value("Jan Rekruter <hr@wiadomosci.example>"))
                .andExpect(jsonPath("$.subject").value(containsString("Elixir Developer")))
                .andExpect(jsonPath("$.body").value(containsString("w firmie Wiadomości SA")))
                .andExpect(jsonPath("$.eventId").value(nullValue()));
        mvc.perform(get("/api/applications/" + id + "/events")).andExpect(jsonPath("$", hasSize(1)));

        postJson("/api/applications/" + id + "/messages",
                "{\"templateId\":\"follow-up\",\"save\":true,\"body\":\"Edytowana treść\"}")
                .andExpect(jsonPath("$.eventId").value(notNullValue()))
                .andExpect(jsonPath("$.body").value("Edytowana treść"));
        mvc.perform(get("/api/applications/" + id + "/events"))
                .andExpect(jsonPath("$[?(@.type == 'MESSAGE')].note").value(hasItem(containsString("Edytowana treść"))));

        postJson("/api/applications/" + id + "/messages", "{\"templateId\":\"nie-ma\"}").andExpect(status().isNotFound());
        postJson("/api/applications/" + id + "/messages", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void reapplyCheckFindsSameCompany() throws Exception {
        long first = offer("Data Engineer", "Firma Powtórka S.A.");
        long previous = application(first, "REJECTED", null);
        long second = offer("Data Engineer (Mid)", "Firma Powtórka");

        mvc.perform(get("/api/applications/check").param("offerId", String.valueOf(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedCompany").value("firma powtorka"))
                .andExpect(jsonPath("$.sameCompany[0].applicationId").value(previous))
                .andExpect(jsonPath("$.sameCompany[0].outcome").value("Odrzucona"))
                .andExpect(jsonPath("$.warning").value(containsString("Firma Powtórka")))
                .andExpect(jsonPath("$.sameOffer", hasSize(0)));
        mvc.perform(get("/api/applications/check").param("offerId", String.valueOf(first)))
                .andExpect(jsonPath("$.sameOffer[0].applicationId").value(previous))
                .andExpect(jsonPath("$.warning").value(nullValue()));
        mvc.perform(get("/api/applications/check")).andExpect(status().isBadRequest());
    }

    @Test
    void coverLetterOfflineUsesVerbatimCvQuotes() throws Exception {
        long offerId = offer("Java Developer", "List Motywacyjny sp. z o.o.");
        String json = postJson("/api/cover-letters", "{\"offerId\":" + offerId + ",\"tone\":\"swobodny\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("offline"))
                .andExpect(jsonPath("$.tone").value("swobodny"))
                .andExpect(jsonPath("$.text").value(containsString("List Motywacyjny sp. z o.o.")))
                .andExpect(jsonPath("$.evidence", not(hasSize(0))))
                .andReturn().getResponse().getContentAsString();
        Number cvId = JsonPath.read(json, "$.cvId");
        String cv = JsonPath.read(mvc.perform(get("/api/cv/" + cvId)).andReturn().getResponse().getContentAsString(),
                "$.content");
        List<String> quotes = JsonPath.read(json, "$.evidence[*].quote");
        assertThat(quotes).isNotEmpty().allSatisfy(q -> assertThat(cv).contains(q));
        List<String> requirements = JsonPath.read(json, "$.evidence[*].requirement");
        assertThat(requirements).doesNotContain("Kubernetes");

        postJson("/api/cover-letters", "{\"offerId\":" + offerId + ",\"tone\":\"wesoły\"}").andExpect(status().isBadRequest());
        postJson("/api/cover-letters", "{\"language\":\"en\"}").andExpect(status().isBadRequest());
        postJson("/api/cover-letters", "{\"offerId\":" + offerId + ",\"language\":\"en\"}")
                .andExpect(jsonPath("$.text").value(startsWith("Dear Hiring Team,")));
    }
}

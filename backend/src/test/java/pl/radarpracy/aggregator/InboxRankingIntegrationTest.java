package pl.radarpracy.aggregator;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracja skrzynki agregatora z modułem rankingu: pozycja w rankingu i wykluczone oferty na końcu. */
@SpringBootTest
@AutoConfigureMockMvc
class InboxRankingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void inboxIsSortedByRankWithExcludedLast() throws Exception {
        String json = mvc.perform(get("/api/aggregator/inbox")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Boolean> excluded = JsonPath.read(json, "$.items[*].excluded");
        List<Object> ranks = JsonPath.read(json, "$.items[*].rankScore");
        assertThat(excluded).isNotEmpty();
        assertThat(ranks).doesNotContainNull();
        int firstExcluded = excluded.indexOf(true);
        if (firstExcluded >= 0) {
            assertThat(excluded.subList(firstExcluded, excluded.size())).containsOnly(true);
        }
    }
}

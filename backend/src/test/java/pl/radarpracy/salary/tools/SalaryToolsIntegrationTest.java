package pl.radarpracy.salary.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SalaryToolsIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String createOffer(String body) throws Exception {
        String json = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1");
    }

    @Test
    void netCalculatorEndpoint() throws Exception {
        mvc.perform(post("/api/salary/net").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":4806,\"contract\":\"UOP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.netMonthly").value(3605.85))
                .andExpect(jsonPath("$.assumptions", notNullValue()))
                .andExpect(jsonPath("$.disclaimer", notNullValue()));
        mvc.perform(post("/api/salary/net").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20000,\"contract\":\"B2B_RYCZALT\",\"ryczaltRate\":12,\"zus\":\"PREFERENTIAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialMonthly").value(456.18));
        mvc.perform(post("/api/salary/net").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5000}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(get("/api/salary/tax-parameters")).andExpect(jsonPath("$.pensionBaseLimit").value(282600.0));
    }

    @Test
    void compareStatsNegotiationAndBenchmark() throws Exception {
        String uop = createOffer("""
                {"title":"Java Dev (porównanie)","company":"Alfa","seniority":"MID","contractTypes":["UOP"],
                 "salaryMin":14000,"salaryMax":18000,"salaryPeriod":"MONTH","salaryGross":true,
                 "benefits":["Opieka medyczna","PPK"]}
                """);
        String b2b = createOffer("""
                {"title":"Java Dev B2B (porównanie)","company":"Beta","seniority":"MID","contractTypes":["B2B"],
                 "salaryMin":20000,"salaryMax":24000,"salaryPeriod":"MONTH","salaryGross":true}
                """);
        mvc.perform(post("/api/salary/compare").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offerIds\":[" + uop + "," + b2b + "],\"perOffer\":{\"" + uop
                                + "\":{\"commuteMonthly\":200}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers", hasSize(2)))
                .andExpect(jsonPath("$.offers[0].rank").value(1))
                .andExpect(jsonPath("$.offers[?(@.offerId == " + b2b + ")].paidLeave").value(false))
                .andExpect(jsonPath("$.offers[?(@.offerId == " + uop + ")].commuteMonthly").value(200.0));
        mvc.perform(post("/api/salary/compare").contentType(MediaType.APPLICATION_JSON).content("{\"offerIds\":[]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/salary/stats").param("seniority", "MID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.count", greaterThan(1)))
                .andExpect(jsonPath("$.overall.median", notNullValue()))
                .andExpect(jsonPath("$.bySeniority[0].key").value("MID"));
        mvc.perform(get("/api/salary/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.byContract", notNullValue()));

        mvc.perform(get("/api/offers/" + b2b + "/negotiation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchor.comparable", notNullValue()))
                .andExpect(jsonPath("$.anchor.text").value(org.hamcrest.Matchers.containsString("netto + VAT")))
                .andExpect(jsonPath("$.phrases", notNullValue()))
                .andExpect(jsonPath("$.levers", notNullValue()));

        mvc.perform(get("/api/offers/" + uop + "/salary-benchmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryPresent").value(true))
                .andExpect(jsonPath("$.verdict", notNullValue()))
                .andExpect(jsonPath("$.net.contract").value("UOP"));
        mvc.perform(get("/api/offers/999999/negotiation")).andExpect(status().isNotFound());
    }
}

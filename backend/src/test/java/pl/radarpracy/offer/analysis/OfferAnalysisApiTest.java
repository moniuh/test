package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpointy modułu „Pozyskiwanie i analiza ofert” (MockMvc, dane demo w bazie testowej). */
@SpringBootTest
@AutoConfigureMockMvc
class OfferAnalysisApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    OfferService offers;

    private long demoOfferId(String title, String company) {
        return offers.all().stream()
                .filter(o -> o.getTitle().equals(title) && company.equals(o.getCompany()))
                .map(JobOffer::getId).findFirst().orElseThrow();
    }

    private long createOffer(String title, String company, String description) throws Exception {
        String body = """
                {"title":%s,"company":%s,"description":%s}
                """.formatted(json(title), json(company), json(description));
        String res = mvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(res.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    private static String json(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    void parseReturnsDraftWithConfidenceAndReviewList() throws Exception {
        String body = """
                {"text":"Senior Java Developer — FinPay Systems\\nLokalizacja: Warszawa (hybrydowo)\\nB2B 22 000 – 28 000 PLN netto + VAT\\n\\nWymagania:\\n- Java\\n- Kafka","url":"https://justjoin.it/offers/1"}
                """;
        mvc.perform(post("/api/offers/parse").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.title").value("Senior Java Developer"))
                .andExpect(jsonPath("$.draft.company").value("FinPay Systems"))
                .andExpect(jsonPath("$.draft.location").value("Warszawa"))
                .andExpect(jsonPath("$.draft.workMode").value("HYBRID"))
                .andExpect(jsonPath("$.draft.contractTypes[0]").value("B2B"))
                .andExpect(jsonPath("$.draft.salaryMin").value(22000))
                .andExpect(jsonPath("$.draft.source").value("justjoin.it"))
                .andExpect(jsonPath("$.draft.requirements.length()").value(2))
                .andExpect(jsonPath("$.recognized", hasItem("salary")))
                .andExpect(jsonPath("$.reviewThreshold").value(OfferTextParser.REVIEW_THRESHOLD))
                .andExpect(jsonPath("$.fields[0].field").value("title"))
                .andExpect(jsonPath("$.fields[0].evidence").value("Senior Java Developer — FinPay Systems"))
                .andExpect(jsonPath("$.confidence").isNumber());

        mvc.perform(post("/api/offers/parse").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wklej treść ogłoszenia"));
    }

    @Test
    void importUrlRejectsUnsafeAddressesWithoutFetching() throws Exception {
        for (String url : new String[]{"http://localhost:8080/api/offers", "http://127.0.0.1/", "http://169.254.169.254/latest",
                "http://[::1]/", "file:///etc/passwd", "ftp://example.com/a"}) {
            mvc.perform(post("/api/offers/import-url").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":" + json(url) + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"));
        }
        mvc.perform(post("/api/offers/import-url").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analysisOfDemoRedFlagAndScamOffers() throws Exception {
        long redFlags = demoOfferId("Młodszy specjalista ds. obsługi klienta", "Dynamiczny Zespół sp. z o.o.");
        mvc.perform(get("/api/offers/" + redFlags + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("offline"))
                .andExpect(jsonPath("$.redFlags[*].code", hasItem("UNPAID_TRIAL")))
                .andExpect(jsonPath("$.redFlags[*].code", hasItem("UOD_FOR_JOB")))
                .andExpect(jsonPath("$.redFlags[*].code", hasItem("PRIVATE_DATA_REQUESTED")))
                .andExpect(jsonPath("$.redFlags[0].severity").value("high"))
                .andExpect(jsonPath("$.redFlags[?(@.code=='UNPAID_TRIAL')].quote", hasItem("Bezpłatny okres próbny (2 tygodnie)")))
                .andExpect(jsonPath("$.scam.level").value("low"));

        long scam = demoOfferId("Praca zdalna — przyjmowanie paczek, 500 zł dziennie", "Global Parcel Partners");
        mvc.perform(get("/api/offers/" + scam + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scam.level").value("high"))
                .andExpect(jsonPath("$.scam.levelLabel").value("wysokie"))
                .andExpect(jsonPath("$.scam.score", greaterThanOrEqualTo(55)))
                .andExpect(jsonPath("$.scam.signals[*].code", hasItem("PARCEL_MULE")))
                .andExpect(jsonPath("$.scam.recommendations[*]", hasItem(containsString("KRS"))));

        mvc.perform(get("/api/offers/999999/analysis")).andExpect(status().isNotFound());
    }

    @Test
    void demoDuplicatePairIsDetected() throws Exception {
        long original = demoOfferId("Java Developer", "LogiTech Solutions");
        long duplicate = demoOfferId("Java Developer (Mid)", "LogiTech Solutions sp. z o.o.");
        mvc.perform(get("/api/offers/" + duplicate + "/duplicates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threshold").value(DuplicateDetector.THRESHOLD))
                .andExpect(jsonPath("$.weights.company").value(DuplicateDetector.W_COMPANY))
                .andExpect(jsonPath("$.candidates[0].offerId").value(original))
                .andExpect(jsonPath("$.candidates[0].duplicate").value(true))
                .andExpect(jsonPath("$.candidates[0].older").value(true))
                .andExpect(jsonPath("$.candidates[0].companyScore").value(1.0));
    }

    @Test
    void duplicateCheckBeforeSaveAndMarkUnmark() throws Exception {
        String desc = "Wymagania:\n- Elixir i Phoenix\n- PostgreSQL\n- 3 lata doświadczenia\nOferujemy pracę w Gdyni dla zespołu fintech.";
        long first = createOffer("Elixir Developer", "Fiolet Software sp. z o.o.", desc);
        long second = createOffer("Elixir Developer (Mid)", "Fiolet Software", desc + "\nBenefity: karta sportowa.");

        String check = """
                {"title":"Elixir Developer","company":"FIOLET SOFTWARE S.A.","description":%s}
                """.formatted(json(desc));
        mvc.perform(post("/api/offers/duplicates/check").contentType(MediaType.APPLICATION_JSON).content(check))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.offerId==" + first + ")].duplicate", hasItem(true)))
                .andExpect(jsonPath("$.candidates[?(@.offerId==" + second + ")].duplicate", hasItem(true)));
        mvc.perform(post("/api/offers/duplicates/check").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/offers/" + second + "/mark-duplicate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalId\":" + first + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOfId").value(first))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.rejectReason").value("DUPLICATE"))
                .andExpect(jsonPath("$.rejectNote", containsString("Elixir Developer")));
        mvc.perform(get("/api/offers/" + first + "/duplicates"))
                .andExpect(jsonPath("$.candidates[?(@.offerId==" + second + ")].alreadyMarked", hasItem(true)));

        // nie można oznaczyć oryginału jako duplikatu własnego duplikatu ani samego siebie
        mvc.perform(post("/api/offers/" + first + "/mark-duplicate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalId\":" + second + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/offers/" + first + "/mark-duplicate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalId\":" + first + "}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/offers/" + second + "/unmark-duplicate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateOfId").value(nullValue()))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    void rejectWithReasonAndNote() throws Exception {
        long id = createOffer("PHP Developer", "Stara Firma", "Wymagania:\n- PHP 5");
        mvc.perform(post("/api/offers/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"TECH_STACK\",\"note\":\"  Nie chcę wracać do PHP  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("TECH_STACK"))
                .andExpect(jsonPath("$.rejectNote").value("Nie chcę wracać do PHP"));

        mvc.perform(post("/api/offers/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Wybierz powód odrzucenia"));
        mvc.perform(post("/api/offers/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/offers/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"note\":" + json("x".repeat(1001)) + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/offers/999999/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/offers?status=REJECTED"))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
    }
}

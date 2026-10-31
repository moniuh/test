package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.skills.SkillDictionary;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OfferAnalyzerTest {

    private final OfferAnalyzer analyzer = new OfferAnalyzer(new RequirementExtractor(new SkillDictionary()));

    @Test
    void detectsWorkModeContractSeniorityAndExpiry() {
        assertThat(OfferTextAnalyzer.workMode("Praca w 100% zdalna, spotkania w biurze raz na kwartał")).isEqualTo(WorkMode.REMOTE);
        assertThat(OfferTextAnalyzer.workMode("Model hybrydowy: 2 dni w biurze")).isEqualTo(WorkMode.HYBRID);
        assertThat(OfferTextAnalyzer.workMode("Praca stacjonarna w Poznaniu")).isEqualTo(WorkMode.ONSITE);
        assertThat(OfferTextAnalyzer.workMode("Opis bez informacji")).isNull();

        assertThat(OfferTextAnalyzer.contractTypes("Umowa o pracę lub B2B")).containsExactly(ContractType.UOP, ContractType.B2B);
        assertThat(OfferTextAnalyzer.contractTypes("umowa zlecenie, stosujemy dobre praktyki")).containsExactly(ContractType.UZ);
        assertThat(OfferTextAnalyzer.contractTypes("Staż pracy min. 2 lata")).isEmpty();
        // „B2B” jako model biznesu to nie forma umowy
        assertThat(OfferTextAnalyzer.contractTypes("Umowa o pracę. Portfolio z projektami B2B, rynek B2B SaaS")).containsExactly(ContractType.UOP);
        assertThat(OfferTextAnalyzer.contractTypes("Sprzedaż B2B, kontrakt B2B lub UoP")).containsExactly(ContractType.UOP, ContractType.B2B);
        assertThat(OfferTextAnalyzer.contractTypes("Stawka 150 zł/h netto + VAT (B2B)")).containsExactly(ContractType.B2B);
        assertThat(OfferTextAnalyzer.fromEmploymentTypes(List.of("FULL_TIME", "CONTRACTOR", "INTERN")))
                .containsExactly(ContractType.B2B, ContractType.INTERNSHIP);

        assertThat(OfferTextAnalyzer.seniority("Senior Java Developer")).isEqualTo(Seniority.SENIOR);
        assertThat(OfferTextAnalyzer.seniority("Młodszy księgowy")).isEqualTo(Seniority.JUNIOR);
        assertThat(OfferTextAnalyzer.seniority("Java Developer (mid)")).isEqualTo(Seniority.MID);
        assertThat(OfferTextAnalyzer.seniority("Team Lead .NET")).isEqualTo(Seniority.LEAD);
        assertThat(OfferTextAnalyzer.seniority("Stażysta w dziale HR")).isEqualTo(Seniority.INTERN);
        assertThat(OfferTextAnalyzer.seniority("Księgowa")).isNull();

        assertThat(OfferTextAnalyzer.expiresAt("Oferta ważna do: 30.11.2026")).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(OfferTextAnalyzer.expiresAt("Aplikuj do 2026-12-01")).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void analysisBuildsDraftFromDemoFeed() {
        String xml = SourceConnector.applyPlaceholders(
                new String(SourceConnector.loadDemo("demo-feed.xml"), StandardCharsets.UTF_8), LocalDate.of(2026, 10, 31));
        List<FeedItem> items = FeedParser.parse(xml, null).items();
        assertThat(items).hasSize(8);
        Map<String, OfferAnalyzer.Analysis> byExt = items.stream()
                .collect(Collectors.toMap(FeedItem::externalId, i -> analyzer.analyze(i, "Demo"), (a, b) -> a));
        Function<String, JobOffer> draft = id -> byExt.get(id).draft();

        JobOffer java = draft.apply("praca-dzis-1001");
        assertThat(java.getTitle()).isEqualTo("Java Developer (Spring Boot)");
        assertThat(java.getCompany()).isEqualTo("Bitwa Systems");
        assertThat(java.getLocation()).isEqualTo("Warszawa");
        assertThat(java.getSource()).isEqualTo("Demo");
        assertThat(java.getSalaryMin()).isEqualTo(17000);
        assertThat(java.getSalaryMax()).isEqualTo(22000);
        assertThat(java.getSalaryGross()).isTrue();
        assertThat(java.getContractTypes()).contains(ContractType.UOP);
        assertThat(java.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(java.getRequirements()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(java.getRequirements().stream().filter(r -> !r.mustHave()).count()).isEqualTo(2);
        assertThat(java.getPublishedAt()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(byExt.get("praca-dzis-1001").notes()).anyMatch(n -> n.startsWith("Widełki z treści"));
        assertThat(byExt.get("praca-dzis-1001").signals()).isEmpty();

        JobOffer kotlin = draft.apply("praca-dzis-1002");
        assertThat(kotlin.getCompany()).isEqualTo("Paczkomatix");
        assertThat(kotlin.getLocation()).isEqualTo("Gdańsk");
        assertThat(kotlin.getSalaryPeriod()).isEqualTo(SalaryPeriod.HOUR);
        assertThat(kotlin.getContractTypes()).containsExactly(ContractType.B2B);
        assertThat(kotlin.getWorkMode()).isEqualTo(WorkMode.REMOTE);

        OfferAnalyzer.Analysis injected = byExt.get("praca-dzis-1003");
        assertThat(injected.draft().getSeniority()).isEqualTo(Seniority.SENIOR);
        assertThat(injected.draft().getDescription()).doesNotContain("pomarańcza");
        assertThat(injected.signals()).isNotEmpty()
                .allMatch(s -> s.kind() == InjectionSignal.Kind.HIDDEN_TEXT && s.severity() == InjectionSignal.Severity.HIGH);
        assertThat(injected.signals().get(0).quote()).contains("zignoruj poprzednie instrukcje");

        JobOffer qa = draft.apply("praca-dzis-1006");
        assertThat(qa.getExpiresAt()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(qa.getSeniority()).isEqualTo(Seniority.JUNIOR);

        JobOffer analyst = draft.apply("praca-dzis-1007");
        assertThat(analyst.getCompany()).isEqualTo("Sklepy Kwiatek");
        assertThat(analyst.getLocation()).isEqualTo("Kraków");
        assertThat(analyst.getSalaryMin()).isNull();

        JobOffer recruiter = draft.apply("praca-dzis-1008");
        assertThat(recruiter.getSalaryMin()).isNull();
        assertThat(recruiter.getSalaryMax()).isEqualTo(9000);
    }
}

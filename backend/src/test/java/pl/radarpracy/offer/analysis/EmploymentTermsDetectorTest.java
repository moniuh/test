package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmploymentTermsDetectorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Kraków / 100% zdalnie|REMOTE",
            "Praca w pełni zdalna, okazjonalne spotkania w biurze|REMOTE",
            "Fully remote within the EU|REMOTE",
            "Gdańsk / zdalnie. B2B 25 000 PLN|REMOTE",
            "Warszawa (hybrydowo, 2 dni w biurze)|HYBRID",
            "Hybrid work model, Kraków|HYBRID",
            "Praca z biura 3 dni w tygodniu w biurze, reszta z domu|HYBRID",
            "Wrocław, praca z biura. UoP 7 000 zł|ONSITE",
            "Praca stacjonarna w Poznaniu|ONSITE",
            "On-site in our Warsaw office|ONSITE",
            "Warszawa, praca z biura (4 dni w tygodniu)|ONSITE",
            "Nie oferujemy pracy zdalnej, biuro w centrum|ONSITE",
    })
    void detectsWorkMode(String text, WorkMode expected) {
        var d = EmploymentTermsDetector.workMode(text).orElseThrow();
        assertThat(d.value()).isEqualTo(expected);
        assertThat(text).contains(d.quote());
    }

    @Test
    void workModeUnknownWithoutKeywords() {
        assertThat(EmploymentTermsDetector.workMode("Java Developer, Spring Boot, Kafka")).isEmpty();
        assertThat(EmploymentTermsDetector.workMode(null)).isEmpty();
    }

    @Test
    void remoteMentionAndOfficeMentionIsLowConfidence() {
        var d = EmploymentTermsDetector.workMode("Lokalizacja: Warszawa, praca w biurze.\nBenefity: owoce, praca zdalna w piątki")
                .orElseThrow();
        assertThat(d.confidence()).isLessThan(OfferTextParser.REVIEW_THRESHOLD);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Umowa o pracę, 14 000 – 18 000 zł brutto|UOP",
            "UoP: 14 000 PLN brutto/mies.|UOP",
            "Employment contract, full-time|UOP",
            "B2B: 140–170 zł/h netto + VAT|B2B",
            "Kontrakt na fakturę, własna działalność|B2B",
            "Contractor basis, 120 EUR/h|B2B",
            "Forma zatrudnienia: umowa zlecenie|UZ",
            "Mandate contract for students|UZ",
            "Oferujemy umowę o dzieło|UOD",
            "Contract for specific work|UOD",
            "Płatny staż w dziale IT|INTERNSHIP",
            "Praktyki studenckie latem|INTERNSHIP",
            "Summer internship programme|INTERNSHIP",
    })
    void detectsContractType(String text, ContractType expected) {
        var types = EmploymentTermsDetector.contractTypes(text);
        assertThat(types).extracting(EmploymentTermsDetector.Detection::value).contains(expected);
        types.forEach(d -> assertThat(text).contains(d.quote()));
    }

    @Test
    void multipleContractsInOrderOfAppearance() {
        var types = EmploymentTermsDetector.contractTypes("Warszawa / zdalnie. UoP 16 000 – 20 000 zł brutto lub B2B.");
        assertThat(types).extracting(EmploymentTermsDetector.Detection::value).containsExactly(ContractType.UOP, ContractType.B2B);
    }

    @Test
    void experienceIsNotInternshipAndGoodPracticesAreNotPraktyki() {
        String text = """
                Wymagania:
                - min. 3 lata stażu w branży IT
                - staż pracy w zespole Scrum
                - znajomość dobrych praktyk programowania
                - international team, internal tools
                """;
        assertThat(EmploymentTermsDetector.contractTypes(text)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Senior Java Developer|SENIOR",
            "Java Developer (Mid)|MID",
            "Młodszy specjalista ds. obsługi klienta|JUNIOR",
            "Junior Python Developer|JUNIOR",
            "Tech Lead Java|LEAD",
            "Senior Tech Lead|LEAD",
            "Stażysta w dziale analiz|INTERN",
            "Starszy specjalista ds. kadr|SENIOR",
            "Regular Frontend Developer|MID",
    })
    void seniorityFromTitle(String title, Seniority expected) {
        var d = EmploymentTermsDetector.seniority(title, null, null).orElseThrow();
        assertThat(d.value()).isEqualTo(expected);
        assertThat(d.quote()).isEqualTo(title);
    }

    @Test
    void ambiguousTitleHasLowerConfidence() {
        var d = EmploymentTermsDetector.seniority("Junior/Mid Java Developer", null, null).orElseThrow();
        assertThat(d.value()).isEqualTo(Seniority.JUNIOR);
        assertThat(d.confidence()).isLessThan(0.75);
    }

    @Test
    void seniorityFromYearsOfExperience() {
        String text = "Wymagania:\n- Minimum 5 lat doświadczenia w Java\n- Kafka";
        List<Requirement> reqs = List.of(new Requirement("Minimum 5 lat doświadczenia w Java", true, 5),
                Requirement.must("Kafka"), new Requirement("10 lat z COBOL", false, 10));
        var d = EmploymentTermsDetector.seniority("Java Developer", text, reqs).orElseThrow();
        assertThat(d.value()).isEqualTo(Seniority.SENIOR);
        assertThat(d.quote()).isEqualTo("Minimum 5 lat doświadczenia w Java");
        assertThat(d.confidence()).isLessThan(0.75);

        var mid = EmploymentTermsDetector.seniority("Developer", "", List.of(new Requirement("3 years with Go", true, null)));
        assertThat(mid.orElseThrow().value()).isEqualTo(Seniority.MID);
    }

    @Test
    void seniorityFromExplicitLevelOrNoExperience() {
        assertThat(EmploymentTermsDetector.seniority("Java Developer", "Szukamy osoby na poziomie senior.", List.of())
                .orElseThrow().value()).isEqualTo(Seniority.SENIOR);
        assertThat(EmploymentTermsDetector.seniority("Konsultant", "Praca bez doświadczenia, przyuczymy.", List.of())
                .orElseThrow().value()).isEqualTo(Seniority.JUNIOR);
        assertThat(EmploymentTermsDetector.seniority("Konsultant", "Obsługa klienta.", List.of())).isEmpty();
    }
}

package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.skills.SkillDictionary;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OfferTextParserTest {

    private final OfferTextParser parser = new OfferTextParser(new RequirementExtractor(new SkillDictionary()));

    static final String FINPAY = """
            Senior Java Developer — FinPay Systems
            Lokalizacja: Warszawa (hybrydowo, 2 dni w biurze)
            Wynagrodzenie: 22 000 – 28 000 PLN netto + VAT (B2B)

            O projekcie:
            Budujemy platformę płatności natychmiastowych dla banków w Europie Środkowej.

            Wymagania:
            - Minimum 5 lat doświadczenia komercyjnego w Java (17+)
            - Spring Boot i mikroserwisy w produkcji
            - Apache Kafka

            Mile widziane:
            - AWS

            Oferujemy:
            - Kontrakt B2B, 26 dni płatnej przerwy
            - Prywatną opiekę medyczną
            - Budżet szkoleniowy 5 000 zł rocznie

            Oferta ważna do 30.11.2026.
            """;

    private static Map<String, ParsedField> byName(ParsedOffer p) {
        return p.fields().stream().collect(Collectors.toMap(ParsedField::field, Function.identity()));
    }

    @Test
    void parsesFullOfferWithHeaderAndSections() {
        ParsedOffer p = parser.parse(FINPAY, "https://justjoin.it/offers/finpay-senior-java");
        OfferDraft d = p.draft();
        assertThat(d.title()).isEqualTo("Senior Java Developer");
        assertThat(d.company()).isEqualTo("FinPay Systems");
        assertThat(d.location()).isEqualTo("Warszawa");
        assertThat(d.workMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(d.contractTypes()).containsExactly(ContractType.B2B);
        assertThat(d.seniority()).isEqualTo(Seniority.SENIOR);
        assertThat(d.salaryMin()).isEqualTo(22000);
        assertThat(d.salaryMax()).isEqualTo(28000);
        assertThat(d.salaryCurrency()).isEqualTo("PLN");
        assertThat(d.salaryPeriod()).isEqualTo(SalaryPeriod.MONTH);
        assertThat(d.salaryGross()).isTrue();
        assertThat(d.requirements()).hasSize(4);
        assertThat(d.requirements().get(3).mustHave()).isFalse();
        assertThat(d.benefits()).containsExactly("Kontrakt B2B, 26 dni płatnej przerwy", "Prywatną opiekę medyczną",
                "Budżet szkoleniowy 5 000 zł rocznie");
        assertThat(d.source()).isEqualTo("justjoin.it");
        assertThat(d.url()).isEqualTo("https://justjoin.it/offers/finpay-senior-java");
        assertThat(d.expiresAt()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(d.description()).isEqualTo(FINPAY.strip());

        assertThat(p.recognized()).contains("title", "company", "location", "salary", "workMode", "contractTypes",
                "seniority", "requirements", "benefits", "source", "expiresAt");
        assertThat(p.toReview()).isEmpty();
        assertThat(p.confidence()).isGreaterThan(0.8);
        assertThat(p.origin()).isEqualTo("text");
        assertThat(byName(p).get("location").method()).contains("Lokalizacja");
    }

    @Test
    void everyEvidenceIsVerbatimFragmentOfSource() {
        ParsedOffer p = parser.parse(FINPAY, null);
        for (ParsedField f : p.fields()) {
            if (f.evidence() != null) {
                assertThat(FINPAY).as(f.field()).contains(f.evidence());
            }
        }
    }

    @Test
    void companyFromSecondLineAndCityFromHeader() {
        String text = """
                Java Developer
                LogiTech Solutions — Kraków / 100% zdalnie
                Umowa o pracę, 14 000 – 18 000 zł brutto miesięcznie

                Wymagania:
                - 3 lata doświadczenia w Java

                Oferujemy: karta sportowa, opieka medyczna, PPK, praca zdalna.
                """;
        ParsedOffer p = parser.parse(text, null);
        assertThat(p.draft().title()).isEqualTo("Java Developer");
        assertThat(p.draft().company()).isEqualTo("LogiTech Solutions");
        assertThat(p.draft().location()).isEqualTo("Kraków");
        assertThat(p.draft().workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(p.draft().contractTypes()).containsExactly(ContractType.UOP);
        assertThat(p.draft().seniority()).isEqualTo(Seniority.MID);
        assertThat(p.draft().benefits()).containsExactly("karta sportowa", "opieka medyczna", "PPK", "praca zdalna");
        // firma z heurystyki „druga linia” i poziom z lat doświadczenia → do sprawdzenia
        assertThat(p.toReview()).contains("company", "seniority");
        assertThat(p.draft().source()).isNull();
    }

    @Test
    void companyWithLegalFormAndMissingFieldsMarkedForReview() {
        String text = """
                Młodszy specjalista ds. obsługi klienta
                Dynamiczny Zespół sp. z o.o., Warszawa

                Wymagania:
                - Komunikatywność
                """;
        ParsedOffer p = parser.parse(text, null);
        assertThat(p.draft().title()).isEqualTo("Młodszy specjalista ds. obsługi klienta");
        assertThat(p.draft().company()).isEqualTo("Dynamiczny Zespół sp. z o.o.");
        assertThat(p.draft().location()).isEqualTo("Warszawa");
        assertThat(p.draft().seniority()).isEqualTo(Seniority.JUNIOR);
        assertThat(p.draft().salaryMin()).isNull();
        assertThat(p.toReview()).contains("salary", "workMode", "contractTypes");
        assertThat(byName(p).get("salary").confidence()).isZero();
        assertThat(p.warnings()).contains(OfferTextParser.NO_SALARY_WARNING);
    }

    @Test
    void labelledFieldsWin() {
        String text = """
                Oferta pracy
                Stanowisko: Analityk danych (k/m)
                Firma: Dane i Spółka
                Miejsce pracy: Gdynia
                Praca stacjonarna, umowa zlecenie, 45 zł/h brutto.
                """;
        ParsedOffer p = parser.parse(text, "https://www.example.org/praca/123");
        assertThat(p.draft().title()).isEqualTo("Analityk danych");
        assertThat(p.draft().company()).isEqualTo("Dane i Spółka");
        assertThat(p.draft().location()).isEqualTo("Gdynia");
        assertThat(p.draft().workMode()).isEqualTo(WorkMode.ONSITE);
        assertThat(p.draft().contractTypes()).containsExactly(ContractType.UZ);
        assertThat(p.draft().salaryPeriod()).isEqualTo(SalaryPeriod.HOUR);
        assertThat(p.draft().source()).isEqualTo("example.org");
        assertThat(byName(p).get("company").confidence()).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void englishOfferRemoteWithoutCity() {
        String text = """
                # Java Developer (remote, EU) at Nordlys Software AB
                Fully remote within the EU. B2B contract: 5 500 – 6 500 EUR per month.

                Requirements:
                - 3+ years of commercial experience with Java
                - Spring Boot

                What we offer:
                - Workation
                - Training budget
                """;
        ParsedOffer p = parser.parse(text, null);
        assertThat(p.draft().title()).isEqualTo("Java Developer (remote, EU)");
        assertThat(p.draft().company()).isEqualTo("Nordlys Software AB");
        assertThat(p.draft().location()).isEqualTo("zdalnie");
        assertThat(p.draft().salaryCurrency()).isEqualTo("EUR");
        assertThat(p.draft().contractTypes()).containsExactly(ContractType.B2B);
        assertThat(p.draft().benefits()).containsExactly("Workation", "Training budget");
    }

    @Test
    void trainingBudgetIsNotTakenAsSalary() {
        String text = """
                Backend Developer — Acme
                Oferujemy:
                - budżet szkoleniowy 5 000 zł rocznie
                - dofinansowanie karty sportowej 100 zł
                """;
        ParsedOffer p = parser.parse(text, null);
        assertThat(p.draft().salaryMin()).isNull();
        assertThat(p.draft().salaryMax()).isNull();
        assertThat(OfferTextParser.findSalary("Budżet szkoleniowy 3 000 zł\nWynagrodzenie: 12 000 – 15 000 zł brutto"))
                .get().extracting(r -> r.min()).isEqualTo(12000);
    }

    @Test
    void salaryContractHintAddsContractType() {
        ParsedOffer p = parser.parse("DevOps Engineer — CloudNine\n25 000 – 32 000 PLN netto + VAT", null);
        assertThat(p.draft().contractTypes()).contains(ContractType.B2B);
    }

    @Test
    void scamLikeShoutingTitleStillParsed() {
        String text = """
                PRACA ZDALNA!!! 500 zł DZIENNIE bez doświadczenia
                Szukamy osób do przyjmowania paczek w domu.
                """;
        ParsedOffer p = parser.parse(text, null);
        assertThat(p.draft().title()).isEqualTo("PRACA ZDALNA!!! 500 zł DZIENNIE bez doświadczenia");
        assertThat(p.draft().salaryMin()).isEqualTo(500);
        assertThat(p.draft().salaryPeriod()).isEqualTo(SalaryPeriod.DAY);
        assertThat(p.draft().company()).isNull();
        assertThat(p.toReview()).contains("company");
    }

    @Test
    void sourceFromKnownHosts() {
        assertThat(SourceResolver.fromUrl("https://www.pracuj.pl/praca/x,oferta,123")).contains("pracuj.pl");
        assertThat(SourceResolver.fromUrl("https://pl.linkedin.com/jobs/view/1")).contains("LinkedIn");
        assertThat(SourceResolver.fromUrl("nofluffjobs.com/pl/job/abc")).contains("nofluffjobs.com");
        assertThat(SourceResolver.fromUrl("https://kariera.firma.pl/oferta")).contains("kariera.firma.pl");
        assertThat(SourceResolver.fromUrl("")).isEmpty();
        assertThat(SourceResolver.fromUrl("http://")).isEmpty();
    }

    @Test
    void citiesRequireCapitalLetterAndHandleInflection() {
        assertThat(PolishCities.find("Biuro w Krakowie i we Wrocławiu")).extracting(PolishCities.CityHit::city)
                .containsExactly("Kraków", "Wrocław");
        assertThat(PolishCities.find("chętnie do poznania nowych osób, łodzi nie mamy")).isEmpty();
        assertThat(PolishCities.find("Zielona Góra / Bielsko-Biała")).extracting(PolishCities.CityHit::city)
                .containsExactly("Zielona Góra", "Bielsko-Biała");
    }

    @Test
    void benefitsStopAtNextSectionAndParagraph() {
        String text = """
                Oferujemy:
                - Umowę o dzieło
                - Możliwość szybkiego awansu

                Prosimy o CV ze zdjęciem.
                Wymagania:
                - Java
                """;
        assertThat(OfferSections.benefits(text)).containsExactly("Umowę o dzieło", "Możliwość szybkiego awansu");
    }
}

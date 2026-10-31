package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.profile.cv.CvAuditService.AuditCheck;
import pl.radarpracy.profile.cv.CvAuditService.CheckStatus;
import pl.radarpracy.profile.cv.CvAuditService.CvAudit;
import pl.radarpracy.skills.SkillDictionary;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvAuditServiceTest {

    private final CvAuditService service = new CvAuditService(new SkillDictionary(), null);

    /** CV spełniające wszystkie kontrole (263 słowa). */
    static final String GOOD_CV = """
            Ewa Przykładowa
            Backend Developer
            ewa.przykladowa@example.com | +48 501 234 567 | Kraków

            PODSUMOWANIE
            Backend developerka z sześcioletnim doświadczeniem w budowie systemów finansowych. Projektuję skalowalne usługi, dbam o jakość kodu i chętnie dzielę się wiedzą w zespole. Szukam roli, w której połączę rozwój produktu z mentoringiem.

            DOŚWIADCZENIE ZAWODOWE
            Senior Backend Developer — BankTech S.A., Kraków (03.2021 – obecnie)
            - Zaprojektowałam architekturę mikroserwisów w Java 17 i Spring Boot obsługującą 2 mln transakcji dziennie.
            - Skróciłam czas odpowiedzi API o 45% dzięki buforowaniu w Redis i optymalizacji zapytań do PostgreSQL.
            - Wdrożyłam pipeline CI/CD w GitLab CI, co zmniejszyło czas wydania nowej wersji z 2 dni do 30 minut.
            - Poprowadziłam migrację kolejek z RabbitMQ na Apache Kafka bez przestojów dla 300 tys. użytkowników.
            - Przeszkoliłam 4 osoby z zespołu z testowania kontraktowego i praktyk code review.

            Backend Developer — SoftHouse sp. z o.o., Kraków (09.2018 – 02.2021)
            - Zbudowałam moduł rozliczeń w Spring i Hibernate, który obsługiwał 120 klientów biznesowych.
            - Zautomatyzowałam testy regresyjne w JUnit i Mockito, zwiększając pokrycie kodu z 40% do 85%.
            - Zoptymalizowałam raporty SQL, oszczędzając zespołowi analityków około 10 godzin pracy tygodniowo.
            - Współtworzyłam dokumentację REST API w OpenAPI razem z zespołem frontendu i działem wsparcia.

            PROJEKTY
            - Stworzyłam otwartoźródłową bibliotekę do walidacji numerów IBAN, pobraną ponad 5000 razy z Maven Central.

            UMIEJĘTNOŚCI
            Java, Spring Boot, Hibernate, PostgreSQL, Redis, Apache Kafka, RabbitMQ, Docker, Kubernetes, GitLab CI, JUnit, Mockito, REST API, Git, Linux, Scrum

            WYKSZTAŁCENIE
            AGH w Krakowie — Informatyka, magister inżynier (2013 – 2018)

            JĘZYKI
            Angielski — C1 (codzienna praca w międzynarodowym zespole)
            Niemiecki — A2

            CERTYFIKATY
            Oracle Certified Professional: Java SE 17 Developer (2022)
            AWS Certified Cloud Practitioner (2023)
            """;

    static final String POOR_CV = """
            Jan Kowalski
            Ja jestem programistą. Ja lubię moją pracę. Mój kod jest dobry.
            Ja pracuję w mojej firmie. Moim celem jest rozwój. Ja chcę się uczyć.
            """;

    private static Map<String, AuditCheck> byCode(CvAudit a) {
        return a.checks().stream().collect(Collectors.toMap(AuditCheck::code, Function.identity()));
    }

    @Test
    void weightsSumToHundred() {
        CvAudit a = service.audit(GOOD_CV);
        assertThat(a.checks()).hasSize(13);
        assertThat(a.checks().stream().mapToInt(AuditCheck::weight).sum()).isEqualTo(100);
        assertThat(a.checks()).extracting(AuditCheck::code).doesNotHaveDuplicates();
    }

    @Test
    void goodCvPassesAllChecks() {
        CvAudit a = service.audit(GOOD_CV);
        assertThat(a.checks()).allSatisfy(c -> {
            assertThat(c.status()).as(c.code() + ": " + c.details()).isEqualTo(CheckStatus.OK);
            assertThat(c.fix()).isNull();
        });
        assertThat(a.score()).isEqualTo(100);
        assertThat(a.grade()).isEqualTo("Bardzo dobrze przygotowane CV");
        assertThat(a.stats().words()).isBetween(CvAuditService.WORDS_WARNING_MIN, CvAuditService.WORDS_WARNING_MAX);
        assertThat(a.stats().bullets()).isEqualTo(10);
        assertThat(a.stats().recognizedSkills()).contains("Java", "Kubernetes", "Angielski");
    }

    @Test
    void poorCvGetsLowDeterministicScoreWithFixes() {
        CvAudit a = service.audit(POOR_CV);
        Map<String, AuditCheck> c = byCode(a);
        assertThat(c.get("contact_email").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("contact_phone").status()).isEqualTo(CheckStatus.WARNING);
        assertThat(c.get("section_experience").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("section_skills").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("section_education").status()).isEqualTo(CheckStatus.WARNING);
        assertThat(c.get("length").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("measurable_results").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("experience_dates").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("skills_recognized").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("first_person").status()).isEqualTo(CheckStatus.ERROR);
        assertThat(c.get("first_person").examples()).isNotEmpty().allSatisfy(e -> assertThat(POOR_CV).contains(e));
        assertThat(a.checks()).filteredOn(x -> x.status() != CheckStatus.OK).allSatisfy(x -> assertThat(x.fix()).isNotBlank());
        // 0+2.5+0+0+3+2.5+0+5+0+0+0+6+0 = 19
        assertThat(a.score()).isEqualTo(19);
        assertThat(service.audit(POOR_CV)).isEqualTo(a);
    }

    @Test
    void pointsMatchStatusAndWeight() {
        for (AuditCheck c : service.audit(POOR_CV).checks()) {
            double expected = switch (c.status()) {
                case OK -> c.weight();
                case WARNING -> c.weight() * CvAuditService.WARNING_CREDIT;
                case ERROR -> 0;
            };
            assertThat(c.points()).as(c.code()).isEqualTo(expected);
        }
    }

    @Test
    void actionVerbsInPolishAndEnglish() {
        assertThat(CvAuditService.actionKind("Wdrożyłam system płatności")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Zaprojektowałem API")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Zoptymalizował zapytania")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Projektuję i rozwijam usługi")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Led a team of 5 engineers")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Built an ETL pipeline")).isEqualTo(CvAuditService.ActionKind.VERB);
        assertThat(CvAuditService.actionKind("Wdrożenie testów")).isEqualTo(CvAuditService.ActionKind.NOMINAL);
        assertThat(CvAuditService.actionKind("Migracja na Kafka")).isEqualTo(CvAuditService.ActionKind.NOMINAL);
        assertThat(CvAuditService.actionKind("Code review i mentoring")).isEqualTo(CvAuditService.ActionKind.NONE);
    }

    @Test
    void nominalBulletsGiveWarning() {
        String cv = GOOD_CV.replace("- Zaprojektowałam architekturę", "- Architektura")
                .replace("- Skróciłam czas", "- Skrócenie czasu")
                .replace("- Wdrożyłam pipeline", "- Wdrożenie pipeline'u")
                .replace("- Poprowadziłam migrację", "- Migracja")
                .replace("- Przeszkoliłam", "- Szkolenie dla")
                .replace("- Zbudowałam moduł", "- Budowa modułu")
                .replace("- Zautomatyzowałam testy", "- Automatyzacja testów")
                .replace("- Zoptymalizowałam raporty", "- Optymalizacja raportów")
                .replace("- Współtworzyłam dokumentację", "- Dokumentacja");
        AuditCheck verbs = byCode(service.audit(cv)).get("action_verbs");
        assertThat(verbs.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(verbs.examples()).isNotEmpty().allSatisfy(e -> assertThat(cv).contains(e));
    }

    @Test
    void englishHeadingsInlineLabelsAndDateFormats() {
        String cv = """
                John Example, john@example.com, +44 7700 900123
                EXPERIENCE
                Developer at Acme (since 2020)
                - Led migration of 12 services to Kubernetes, cutting costs by 30%.
                Education: BSc Computer Science (2015 - 2019)
                Skills: Java, Kotlin, Docker
                Languages: English C2, Polish B1
                """;
        Map<String, AuditCheck> c = byCode(service.audit(cv));
        assertThat(c.get("section_experience").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("section_education").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("section_skills").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("section_languages").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("experience_dates").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("contact_phone").status()).isEqualTo(CheckStatus.OK);
        assertThat(c.get("action_verbs").status()).isEqualTo(CheckStatus.OK);
    }

    @Test
    void longBulletsAreReportedWithVerbatimPrefix() {
        String longBullet = "- Odpowiadałam za " + "bardzo ".repeat(40) + "długi opis.";
        String cv = GOOD_CV.replace("- Przeszkoliłam 4 osoby", longBullet + "\n- Przeszkoliłam 4 osoby");
        AuditCheck c = byCode(service.audit(cv)).get("long_bullets");
        assertThat(c.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(c.examples()).singleElement().satisfies(e -> {
            assertThat(e).startsWith("Odpowiadałam za bardzo");
            assertThat(cv).contains(e);
        });
    }

    @Test
    void romanNumeralIsNotAPronoun() {
        String cv = GOOD_CV.replace("magister inżynier", "studia I stopnia, I miejsce w konkursie");
        assertThat(byCode(service.audit(cv)).get("first_person").status()).isEqualTo(CheckStatus.OK);
        String english = GOOD_CV + "\nI led the team. I built the system. My role was key. I've grown. Me too.";
        assertThat(byCode(service.audit(english)).get("first_person").status()).isEqualTo(CheckStatus.WARNING);
    }

    @Test
    void emptyCv() {
        CvAudit a = service.audit("");
        assertThat(a.score()).isBetween(0, 30);
        assertThat(a.stats().words()).isZero();
    }
}

package pl.radarpracy.tailoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvStructureParserTest {

    @Test
    void parsesBackendDemoCv() {
        ParsedCv cv = CvStructureParser.parse(Demo.cvBackend());
        assertThat(cv.sections()).extracting(ParsedCv.Section::kind).containsExactly(CvSectionKind.HEADER,
                CvSectionKind.SUMMARY, CvSectionKind.EXPERIENCE, CvSectionKind.SKILLS, CvSectionKind.LANGUAGES,
                CvSectionKind.EDUCATION);
        ParsedCv.Section header = cv.sections().get(0);
        assertThat(header.heading()).isNull();
        assertThat(header.lines()).hasSize(6).first().isEqualTo("Anna Nowak");
        assertThat(cv.first(CvSectionKind.EXPERIENCE).orElseThrow().heading()).isEqualTo("DOŚWIADCZENIE");

        List<ParsedCv.Role> roles = cv.roles();
        assertThat(roles).hasSize(2);
        assertThat(roles.get(0).header()).isEqualTo("Java Developer — PayFlow sp. z o.o., Warszawa (2022 – obecnie)");
        assertThat(roles.get(0).bullets()).hasSize(5);
        assertThat(roles.get(0).bullets().get(0).text())
                .isEqualTo("Rozwój mikroserwisów płatniczych w Java 17 i Spring Boot (REST API, Hibernate, PostgreSQL).");
        assertThat(roles.get(0).bullets().get(0).marker()).isEqualTo("- ");
        assertThat(roles.get(1).header()).startsWith("Junior Java Developer — SoftHouse");
        assertThat(roles.get(1).bullets()).hasSize(2);
        assertThat(roles.get(1).identityLines()).containsExactly(roles.get(1).header());
        // klauzula RODO zostaje w ostatniej sekcji, dosłownie
        assertThat(cv.first(CvSectionKind.EDUCATION).orElseThrow().nonBlankLines()).hasSize(2);
        assertThat(cv.upperCaseHeadings()).isTrue();
    }

    @Test
    void parsesFullstackDemoCv() {
        ParsedCv cv = CvStructureParser.parse(Demo.cvFullstack());
        assertThat(cv.sections()).extracting(ParsedCv.Section::kind).containsExactly(CvSectionKind.HEADER,
                CvSectionKind.SUMMARY, CvSectionKind.EXPERIENCE, CvSectionKind.SKILLS, CvSectionKind.LANGUAGES,
                CvSectionKind.EDUCATION);
        assertThat(cv.sections().get(0).lines()).hasSize(4);
        assertThat(cv.roles()).hasSize(2);
        assertThat(cv.roles().get(0).bullets()).hasSize(4);
        assertThat(cv.roles().get(1).bullets()).extracting(ParsedCv.Bullet::text)
                .containsExactly("Spring MVC, MySQL, HTML, CSS, JavaScript.");
        assertThat(cv.first(CvSectionKind.SKILLS).orElseThrow().lines())
                .containsExactly("Java, Spring Boot, Hibernate, PostgreSQL, Kafka, React, TypeScript, Redux, HTML, CSS, Jest, Docker, Git, Scrum.");
    }

    @Test
    void englishHeadingsBulletVariantsDetailsAndContinuations() {
        String text = """
                John Smith
                john@example.com

                ## Professional Summary
                Backend engineer.

                Work Experience:
                Senior Engineer
                Acme Corp | 03.2019 – present
                • Built payment APIs in Go
                  and maintained them for 3 years
                * Led on-call rotation
                1. Wrote docs

                Engineer — Beta Ltd (2015 – 2019)
                Short description of the role without bullets.

                Intern — Gamma (2014)
                - Testing

                CERTIFICATIONS
                - AWS Solutions Architect
                """;
        ParsedCv cv = CvStructureParser.parse(text);
        assertThat(cv.sections()).extracting(ParsedCv.Section::kind).containsExactly(CvSectionKind.HEADER,
                CvSectionKind.SUMMARY, CvSectionKind.EXPERIENCE, CvSectionKind.CERTIFICATES);
        assertThat(cv.upperCaseHeadings()).isFalse();
        List<ParsedCv.Role> roles = cv.roles();
        assertThat(roles).hasSize(3);
        ParsedCv.Role first = roles.get(0);
        assertThat(first.header()).isEqualTo("Senior Engineer");
        assertThat(first.details()).containsExactly("Acme Corp | 03.2019 – present");
        assertThat(first.identityLines()).containsExactly("Senior Engineer", "Acme Corp | 03.2019 – present");
        assertThat(first.bullets()).hasSize(3);
        assertThat(first.bullets().get(0).lines()).hasSize(2);
        assertThat(first.bullets().get(0).text()).isEqualTo("Built payment APIs in Go and maintained them for 3 years");
        assertThat(first.bullets().get(0).marker()).isEqualTo("• ");
        assertThat(first.bullets().get(2).text()).isEqualTo("Wrote docs");
        // stanowisko bez punktów, a po pustej linii kolejne z datą
        assertThat(roles.get(1).bullets()).isEmpty();
        assertThat(roles.get(1).details()).containsExactly("Short description of the role without bullets.");
        assertThat(roles.get(2).header()).isEqualTo("Intern — Gamma (2014)");
        assertThat(CvTailor.isEnglish(cv)).isTrue();
    }

    @Test
    void headingDetectionIsConservative() {
        assertThat(CvStructureParser.headingKind("DOŚWIADCZENIE ZAWODOWE")).isEqualTo(CvSectionKind.EXPERIENCE);
        assertThat(CvStructureParser.headingKind("Doświadczenie zawodowe")).isEqualTo(CvSectionKind.EXPERIENCE);
        assertThat(CvStructureParser.headingKind("Umiejętności:")).isEqualTo(CvSectionKind.SKILLS);
        assertThat(CvStructureParser.headingKind("JĘZYKI PROGRAMOWANIA")).isEqualTo(CvSectionKind.SKILLS);
        assertThat(CvStructureParser.headingKind("Języki obce")).isEqualTo(CvSectionKind.LANGUAGES);
        assertThat(CvStructureParser.headingKind("Projekty")).isEqualTo(CvSectionKind.PROJECTS);
        assertThat(CvStructureParser.headingKind("Certyfikaty i szkolenia")).isEqualTo(CvSectionKind.CERTIFICATES);
        assertThat(CvStructureParser.headingKind("KLUCZOWE KOMPETENCJE POD TO STANOWISKO"))
                .isEqualTo(CvSectionKind.KEY_COMPETENCES);
        // zdania i linie z danymi to nie nagłówki
        assertThat(CvStructureParser.headingKind("Doświadczenie w domenie płatności")).isNull();
        assertThat(CvStructureParser.headingKind("Profil LinkedIn: linkedin.com/in/x")).isNull();
        assertThat(CvStructureParser.headingKind("- Umiejętności")).isNull();
        assertThat(CvStructureParser.headingKind("Angielski — B2")).isNull();
        assertThat(CvStructureParser.headingKind("Java Developer")).isNull();
    }

    @Test
    void textWithoutHeadingsIsOneHeaderBlockAndEmptyTextIsSafe() {
        ParsedCv plain = CvStructureParser.parse("Jan Kowalski\nProgramista Java\n- punkt");
        assertThat(plain.sections()).hasSize(1);
        assertThat(plain.sections().get(0).kind()).isEqualTo(CvSectionKind.HEADER);
        assertThat(plain.roles()).isEmpty();

        ParsedCv empty = CvStructureParser.parse("");
        assertThat(empty.sections()).hasSize(1);
        assertThat(empty.sections().get(0).lines()).isEmpty();
        assertThat(CvStructureParser.parse(null).sections()).hasSize(1);
    }

    @Test
    void bulletsWithoutRoleHeaderGetImplicitRole() {
        ParsedCv cv = CvStructureParser.parse("DOŚWIADCZENIE\n- pierwszy\n- drugi\n");
        assertThat(cv.roles()).hasSize(1);
        assertThat(cv.roles().get(0).header()).isNull();
        assertThat(cv.roles().get(0).bullets()).hasSize(2);
    }
}

package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.profile.cv.CvStructure.SectionType;

import static org.assertj.core.api.Assertions.assertThat;

class CvStructureTest {

    @Test
    void recognizesHeadingsInPolishAndEnglish() {
        assertThat(CvStructure.headingType("DOŚWIADCZENIE ZAWODOWE")).isEqualTo(SectionType.EXPERIENCE);
        assertThat(CvStructure.headingType("== Umiejętności ==")).isEqualTo(SectionType.SKILLS);
        assertThat(CvStructure.headingType("Work Experience:")).isEqualTo(SectionType.EXPERIENCE);
        assertThat(CvStructure.headingType("Języki obce")).isEqualTo(SectionType.LANGUAGES);
        assertThat(CvStructure.headingType("EDUCATION")).isEqualTo(SectionType.EDUCATION);
        // zdanie zaczynające się od słowa kluczowego to nie nagłówek
        assertThat(CvStructure.headingType("Doświadczenie w pracy z klientem biznesowym w branży finansowej i "
                + "ubezpieczeniowej")).isNull();
        assertThat(CvStructure.headingType("Experienced Java developer")).isNull();
        assertThat(CvStructure.headingType("Profil LinkedIn: linkedin.com/in/anna-nowak-dev")).isNull();
    }

    @Test
    void inlineLabelDoesNotChangeCurrentSection() {
        CvStructure cv = CvStructure.parse("""
                DOŚWIADCZENIE
                Developer (2020 – 2023)
                Narzędzia: Jira, Confluence
                - Wdrożyłam system
                """);
        assertThat(cv.hasSection(SectionType.EXPERIENCE)).isTrue();
        assertThat(cv.hasSection(SectionType.SKILLS)).isTrue();
        assertThat(cv.heading(SectionType.SKILLS).text()).isEqualTo("Narzędzia: Jira, Confluence");
        assertThat(cv.linesIn(SectionType.EXPERIENCE)).extracting(CvStructure.Line::text)
                .containsExactly("Developer (2020 – 2023)", "- Wdrożyłam system");
        assertThat(cv.bullets()).singleElement().satisfies(l -> {
            assertThat(l.body()).isEqualTo("Wdrożyłam system");
            assertThat(cv.text().substring(l.start(), l.end())).isEqualTo(l.text());
        });
    }

    @Test
    void bulletsIncludeNumberedAndSymbolLists() {
        assertThat(CvStructure.isBullet("• Punkt")).isTrue();
        assertThat(CvStructure.isBullet("- Punkt")).isTrue();
        assertThat(CvStructure.isBullet("1. Punkt")).isTrue();
        assertThat(CvStructure.isBullet("-Punkt")).isFalse();
        assertThat(CvStructure.isBullet("2021 – 2023")).isFalse();
    }
}

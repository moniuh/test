package pl.radarpracy.tailoring;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.tailoring.GuardReport.Kind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TruthGuardTest {

    private static final SkillDictionary DICT = new SkillDictionary();
    private final TruthGuard guard = new TruthGuard(DICT);
    private final CvTailor tailor = new CvTailor(DICT, guard);
    private final CandidateProfile profile = Demo.profile();

    private String tailored(String offer) {
        return tailor.tailor(Demo.cvBackend(), Demo.requirements(offer), profile, TailoringOptions.defaults()).text();
    }

    @Test
    void offlineResultAlwaysPasses() {
        List<String> offers = List.of("OFFER_FINPAY", "OFFER_LOGITECH", "OFFER_LOGITECH_DUP", "OFFER_MEDIKOS",
                "OFFER_SKLEPIK", "OFFER_DATAWISE", "OFFER_REDFLAGS", "OFFER_SCAM", "OFFER_CLOUDNINE", "OFFER_BANKPOL",
                "OFFER_NORDIC", "OFFER_KREDYTO", "OFFER_RETAILONE", "OFFER_INSURETECH");
        List<TailoringOptions> options = List.of(TailoringOptions.defaults(), new TailoringOptions(true, null, false),
                new TailoringOptions(true, 2, false), new TailoringOptions(false, 1, false));
        for (String cv : List.of(Demo.cvBackend(), Demo.cvFullstack())) {
            for (String offer : offers) {
                for (TailoringOptions o : options) {
                    String text = tailor.tailor(cv, Demo.requirements(offer), profile, o).text();
                    GuardReport r = guard.check(cv, profile, text);
                    assertThat(r.violations()).as(offer + " " + o).isEmpty();
                    assertThat(r.passed()).isTrue();
                }
            }
        }
    }

    @Test
    void reportCountsVerbatimRewrittenGeneratedAndProfileOnlySkills() {
        GuardReport r = guard.check(Demo.cvBackend(), profile, tailored("OFFER_MEDIKOS"));
        assertThat(r.passed()).isTrue();
        assertThat(r.verbatimLines()).isGreaterThan(10);
        assertThat(r.rewrittenLines()).isEqualTo(1); // przestawiona lista umiejętności
        assertThat(r.generatedLines()).isEqualTo(2); // linia podsumowania + kluczowe kompetencje
        assertThat(r.profileOnlySkills()).containsExactlyInAnyOrder("React", "TypeScript");
        // to samo CV bez zmian też przechodzi
        assertThat(guard.check(Demo.cvBackend(), profile, Demo.cvBackend()).passed()).isTrue();
    }

    @Test
    void detectsAddedSkill() {
        String text = tailored("OFFER_FINPAY").replace("KLUCZOWE KOMPETENCJE POD TO STANOWISKO\nJava 17,",
                "KLUCZOWE KOMPETENCJE POD TO STANOWISKO\nKubernetes, Java 17,");
        GuardReport r = guard.check(Demo.cvBackend(), profile, text);
        assertThat(r.passed()).isFalse();
        assertThat(r.violations()).singleElement().satisfies(v -> {
            assertThat(v.kind()).isEqualTo(Kind.NEW_SKILL);
            assertThat(v.detail()).contains("Kubernetes");
            assertThat(v.line()).isGreaterThan(0);
            assertThat(text.split("\n")[v.line() - 1]).contains("Kubernetes");
        });
        // dopisana umiejętność nie jest „umiejętnością z profilu”
        assertThat(r.profileOnlySkills()).doesNotContain("Kubernetes");
    }

    @Test
    void skillsFromProfileAndImpliedSkillsAreAllowed() {
        String text = Demo.cvBackend().replace("UMIEJĘTNOŚCI\n", "UMIEJĘTNOŚCI\nReact, TypeScript\n")
                .replace("Code review i mentoring", "Spring i Java: code review i mentoring");
        GuardReport r = guard.check(Demo.cvBackend(), profile, text);
        assertThat(r.violations()).isEmpty();
        // bez profilu React jest nową umiejętnością
        GuardReport noProfile = guard.check(Demo.cvBackend(), new CandidateProfile(), text);
        assertThat(noProfile.violations()).anyMatch(v -> v.kind() == Kind.NEW_SKILL && v.detail().contains("React"));
    }

    @Test
    void detectsNewNumberPercentAndLanguageLevel() {
        String text = tailored("OFFER_FINPAY")
                .replace("o 40%", "o 60%")
                .replace("Angielski — B2", "Angielski — C1");
        GuardReport r = guard.check(Demo.cvBackend(), profile, text);
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.NEW_NUMBER && v.detail().contains("60%"));
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.NEW_NUMBER && v.detail().contains("C1"));
        // liczba z CV w innej postaci (40 zamiast 40%) jest w porządku, ale 40 jako procent musi być procentem
        assertThat(TruthGuard.numbersIn("o 40 % szybciej, 12.03.1995, +48 600")).containsExactly("40%", "12.03.1995",
                "48", "600");
    }

    @Test
    void detectsChangedRoleDateAndInflatedTitle() {
        String base = tailored("OFFER_FINPAY");
        GuardReport dates = guard.check(Demo.cvBackend(), profile,
                base.replace("Warszawa (2022 – obecnie)", "Warszawa (2020 – obecnie)"));
        assertThat(dates.violations()).anyMatch(v -> v.kind() == Kind.NEW_DATE && v.detail().contains("2020"));
        assertThat(dates.violations()).anyMatch(v -> v.kind() == Kind.NEW_DATE && v.detail().contains("daty stanowiska"));
        assertThat(dates.violations()).noneMatch(v -> v.kind() == Kind.MISSING_ROLE);

        GuardReport title = guard.check(Demo.cvBackend(), profile,
                base.replace("Java Developer — PayFlow", "Senior Java Developer — PayFlow"));
        assertThat(title.violations()).singleElement().satisfies(v -> {
            assertThat(v.kind()).isEqualTo(Kind.NEW_EMPLOYER);
            assertThat(v.detail()).contains("PayFlow");
        });

        GuardReport employer = guard.check(Demo.cvBackend(), profile,
                base.replace("Junior Java Developer — SoftHouse, Warszawa", "Junior Java Developer — Google, Warszawa"));
        assertThat(employer.violations()).anyMatch(v -> v.kind() == Kind.NEW_EMPLOYER);
    }

    @Test
    void detectsRemovedRoleAndInventedBullet() {
        String base = tailored("OFFER_FINPAY");
        String withoutRole = base.replace("Junior Java Developer — SoftHouse, Warszawa (2021 – 2022)\n", "");
        GuardReport removed = guard.check(Demo.cvBackend(), profile, withoutRole);
        assertThat(removed.violations()).anyMatch(v -> v.kind() == Kind.MISSING_ROLE && v.line() == 0
                && v.text().contains("SoftHouse"));

        String invented = base.replace("- Utrzymanie systemu rezerwacji",
                "- Prowadziłam negocjacje z klientami korporacyjnymi\n- Utrzymanie systemu rezerwacji");
        GuardReport r = guard.check(Demo.cvBackend(), profile, invented);
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.UNVERIFIED_LINE
                && v.text().contains("klientami korporacyjnymi"));
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.NEW_SKILL && v.detail().contains("Negocjacje"));
    }

    @Test
    void headerChangesAndForeignSkillListItemsAreFlagged() {
        String text = Demo.cvBackend().replace("Data urodzenia: 12.03.1995 | Stan cywilny: panna", "Kierowca")
                .replace("Linux, Scrum.", "Linux, Scrum, Odporność na stres.");
        GuardReport r = guard.check(Demo.cvBackend(), profile, text);
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.UNVERIFIED_LINE && v.text().equals("Kierowca"));
        assertThat(r.violations()).anyMatch(v -> v.kind() == Kind.UNVERIFIED_LINE
                && v.detail().contains("Odporność na stres"));
    }

    @Test
    void rewriteCheckAcceptsRewordingAndRejectsInvention() {
        TruthGuard.Facts facts = guard.facts(Demo.cvBackend(), profile);
        String original = "Migracja komunikacji między usługami na Apache Kafka; skróciłam czas przetwarzania płatności o 40%.";
        assertThat(guard.checkRewrite(facts, original,
                "Skróciłam czas przetwarzania płatności o 40% dzięki migracji komunikacji między usługami na Apache Kafka.",
                0.4)).isEmpty();
        assertThat(guard.checkRewrite(facts, original,
                "Zaprojektowałam od zera platformę Kafka na Kubernetes dla 20 banków.", 0.4))
                .extracting(GuardReport.Violation::kind)
                .contains(Kind.NEW_SKILL, Kind.NEW_NUMBER, Kind.UNVERIFIED_LINE);
    }

    @Test
    void offlineTailoringOfEnglishCvWithSeparateDateLinePasses() {
        String cv = """
                John Smith
                john@example.com

                SUMMARY
                Backend engineer.

                EXPERIENCE
                Senior Engineer
                Acme Corp | 03.2019 – present
                - Wrote documentation
                - Built payment APIs in Java and Spring Boot

                SKILLS
                - Docker
                - Java
                """;
        CandidateProfile p = new CandidateProfile();
        String text = tailor.tailor(cv, List.of(Requirement.must("Java"), Requirement.nice("Docker")), p,
                TailoringOptions.defaults()).text();
        assertThat(text).contains("- Built payment APIs in Java and Spring Boot\n- Wrote documentation")
                .doesNotContain("- Wrote documentation\n- Built");
        assertThat(text.indexOf("- Java")).isLessThan(text.indexOf("- Docker"));
        assertThat(guard.check(cv, p, text).passed()).isTrue();
        GuardReport moved = guard.check(cv, p, text.replace("03.2019", "01.2018"));
        assertThat(moved.violations()).anyMatch(v -> v.kind() == Kind.NEW_DATE);
    }
}

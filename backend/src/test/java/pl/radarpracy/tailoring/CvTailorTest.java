package pl.radarpracy.tailoring;

import org.junit.jupiter.api.Test;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvTailorTest {

    private static final SkillDictionary DICT = new SkillDictionary();
    private final TruthGuard guard = new TruthGuard(DICT);
    private final CvTailor tailor = new CvTailor(DICT, guard);
    private final MatchService matching = new MatchService(DICT, null, null);

    @Test
    void bulletsAreSortedByRelevanceAndNothingIsLostByDefault() {
        List<Requirement> reqs = List.of(Requirement.must("Apache Kafka"), Requirement.must("Docker"),
                Requirement.nice("Mentoring"));
        TailorDraft d = tailor.tailor(Demo.cvBackend(), reqs, Demo.profile(), TailoringOptions.defaults());
        TailorDraft.RoleChange payflow = d.roles().get(0);
        assertThat(payflow.header()).contains("PayFlow");
        assertThat(payflow.reordered()).isTrue();
        // nowa kolejność: Kafka (3), Docker (3), mentoring (1), potem reszta w oryginalnej kolejności
        List<String> visible = d.bullets().stream().filter(b -> b.id().startsWith("r0")).map(TailorDraft.Line::content)
                .toList();
        assertThat(visible).hasSize(5);
        assertThat(visible.get(0)).contains("Apache Kafka");
        assertThat(visible.get(1)).contains("Docker");
        assertThat(visible.get(2)).contains("mentoring");
        assertThat(visible.get(3)).startsWith("Rozwój mikroserwisów");
        assertThat(visible.get(4)).startsWith("Wdrożenie testów");
        assertThat(payflow.bullets().get(1).newPosition()).isEqualTo(1);
        assertThat(payflow.bullets().get(1).matchedSkills()).contains("Kafka");
        assertThat(payflow.bullets()).noneMatch(TailorDraft.BulletChange::hidden);
    }

    @Test
    void hidingIrrelevantBulletsNeverRemovesRolesOrDates() {
        List<Requirement> reqs = List.of(Requirement.must("Apache Kafka"));
        TailorDraft d = tailor.tailor(Demo.cvBackend(), reqs, Demo.profile(), new TailoringOptions(true, null, false));
        String text = d.text();
        assertThat(text).contains("Java Developer — PayFlow sp. z o.o., Warszawa (2022 – obecnie)")
                .contains("Junior Java Developer — SoftHouse, Warszawa (2021 – 2022)")
                .contains("Migracja komunikacji między usługami na Apache Kafka")
                .doesNotContain("Code review i mentoring");
        // stanowisko bez trafnych punktów zachowuje pierwszy punkt
        TailorDraft.RoleChange softhouse = d.roles().get(1);
        assertThat(softhouse.bullets()).filteredOn(b -> !b.hidden()).hasSize(1);
        assertThat(softhouse.bullets().get(0).hidden()).isFalse();

        TailorDraft limited = tailor.tailor(Demo.cvBackend(), reqs, Demo.profile(),
                new TailoringOptions(false, 1, false));
        assertThat(limited.roles()).allSatisfy(r -> assertThat(r.bullets()).filteredOn(b -> !b.hidden()).hasSize(1));
        assertThat(limited.text()).contains("(2022 – obecnie)").contains("(2021 – 2022)");
    }

    @Test
    void summaryAndCompetencesUseOnlyPossessedSkillsInWeightOrder() {
        // FinPay: Kubernetes, AWS i Terraform — brak w CV i profilu
        TailorDraft d = tailor.tailor(Demo.cvBackend(), Demo.requirements("OFFER_FINPAY"), Demo.profile(),
                TailoringOptions.defaults());
        // brzmienie z CV bazowego („Java 17”, „Apache Kafka”), kolejność wg wag wymagań
        assertThat(d.competences()).startsWith("Java 17", "Spring Boot", "Mikroserwisy")
                .contains("Apache Kafka", "PostgreSQL", "Angielski (B2)")
                .doesNotContain("Kubernetes", "AWS", "Terraform");
        List<String> summary = d.summaryLines();
        assertThat(summary.get(0)).startsWith(CvTailor.FOCUS_PL).contains("Java (4 lata)").contains("Kafka (1 rok)")
                .doesNotContain("Kubernetes").doesNotContain("AWS").doesNotContain("Terraform");
        // oryginalne podsumowanie zostaje dosłownie
        assertThat(summary).contains("Java Developer z 4 latami doświadczenia komercyjnego w fintechu. "
                + "Projektuję i rozwijam mikroserwisy w Spring Boot, dbam o testy i wydajność.");
        String text = d.text();
        assertThat(text).contains("KLUCZOWE KOMPETENCJE POD TO STANOWISKO\nJava 17, Spring Boot");
        assertThat(text.indexOf("PODSUMOWANIE")).isLessThan(text.indexOf("KLUCZOWE KOMPETENCJE"));
        assertThat(text.indexOf("KLUCZOWE KOMPETENCJE")).isLessThan(text.indexOf("DOŚWIADCZENIE"));
        // blok nagłówka bez zmian
        assertThat(text).startsWith(String.join("\n", CvStructureParser.parse(Demo.cvBackend()).sections().get(0)
                .lines()));
    }

    @Test
    void mustHaveSkillsComeBeforeNiceToHaveAndProfileSkillsAreMarked() {
        // Medikos: React i TypeScript są tylko w profilu; Docker jest mile widziany
        TailorDraft d = tailor.tailor(Demo.cvBackend(), Demo.requirements("OFFER_MEDIKOS"), Demo.profile(),
                TailoringOptions.defaults());
        assertThat(d.competences()).contains("React", "TypeScript");
        assertThat(d.competences().indexOf("React")).isLessThan(d.competences().indexOf("Docker"));
        assertThat(d.competences()).last().isEqualTo("Docker");
        assertThat(d.addedFromProfile()).containsExactlyInAnyOrder("React", "TypeScript");
        assertThat(d.competences()).doesNotContain("Jest", "Next.js");
    }

    @Test
    void headlineIsUsedWhenItDoesNotDuplicateSummaryAndOnlyIfTruthful() {
        CandidateProfile p = Demo.profile();
        p.setHeadline("Backend developer w fintechu");
        String cv = "Jan Kowalski\n\nDOŚWIADCZENIE\nDeveloper — Firma (2020 – obecnie)\n- Java i Spring Boot w fintechu\n";
        TailorDraft d = tailor.tailor(cv, List.of(Requirement.must("Java")), p, TailoringOptions.defaults());
        // CV bez podsumowania — sekcja powstaje po nagłówku
        assertThat(d.text()).contains("PODSUMOWANIE\nBackend developer w fintechu.\n" + CvTailor.FOCUS_PL);

        p.setHeadline("Python developer, 9 lat doświadczenia");
        TailorDraft lying = tailor.tailor(cv, List.of(Requirement.must("Java")), p, TailoringOptions.defaults());
        assertThat(lying.text()).doesNotContain("Python");
    }

    @Test
    void skillsSectionPutsMatchingItemsFirst() {
        TailorDraft d = tailor.tailor(Demo.cvBackend(), List.of(Requirement.must("Docker"), Requirement.must("Kafka")),
                Demo.profile(), TailoringOptions.defaults());
        String skills = d.sections().stream().filter(s -> s.kind() == CvSectionKind.SKILLS).findFirst().orElseThrow()
                .lines().get(0).text();
        // Docker jest pierwszym wymaganiem, Kafka drugim
        assertThat(skills).startsWith("Docker, Apache Kafka, Java 17").endsWith("Scrum.");
        assertThat(skills.split(", ")).hasSize(15);
    }

    @Test
    void retailoringDoesNotDuplicateGeneratedLines() {
        List<Requirement> reqs = Demo.requirements("OFFER_FINPAY");
        String once = tailor.tailor(Demo.cvBackend(), reqs, Demo.profile(), TailoringOptions.defaults()).text();
        String twice = tailor.tailor(once, reqs, Demo.profile(), TailoringOptions.defaults()).text();
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void scoreAfterIsAtLeastBeforeAndProfileSkillGainsEvidence() {
        CandidateProfile profile = Demo.profile();
        for (String cv : List.of(Demo.cvBackend(), Demo.cvFullstack())) {
            for (String offer : List.of("OFFER_FINPAY", "OFFER_LOGITECH", "OFFER_MEDIKOS", "OFFER_SKLEPIK",
                    "OFFER_DATAWISE", "OFFER_CLOUDNINE", "OFFER_BANKPOL", "OFFER_NORDIC", "OFFER_KREDYTO")) {
                List<Requirement> reqs = Demo.requirements(offer);
                String tailored = tailor.tailor(cv, reqs, profile, TailoringOptions.defaults()).text();
                MatchResult before = matching.matchRequirements(reqs, cv, profile);
                MatchResult after = matching.matchRequirements(reqs, tailored, profile);
                assertThat(after.score()).as(offer).isGreaterThanOrEqualTo(before.score());
            }
        }
        // oferta demo z umiejętnością tylko w profilu (React, TypeScript) — wynik rośnie
        List<Requirement> medikos = Demo.requirements("OFFER_MEDIKOS");
        String tailored = tailor.tailor(Demo.cvBackend(), medikos, profile, TailoringOptions.defaults()).text();
        ScoreComparison cmp = ScoreComparison.of(matching.matchRequirements(medikos, Demo.cvBackend(), profile),
                matching.matchRequirements(medikos, tailored, profile));
        assertThat(cmp.after()).isGreaterThan(cmp.before());
        ScoreComparison.RequirementChange react = cmp.requirements().stream()
                .filter(r -> r.requirement().contains("React")).findFirst().orElseThrow();
        assertThat(react.change()).isEqualTo(ScoreComparison.Change.GAINED_EVIDENCE);
        assertThat(react.evidenceAfter()).isNotNull();
        assertThat(tailored).contains(react.evidenceAfter());
        assertThat(cmp.requirements()).anyMatch(r -> r.change() == ScoreComparison.Change.STILL_MISSING
                && r.note() != null);
        assertThat(cmp.summary()).contains("tailoring nie dodaje umiejętności, których nie masz");
    }

    @Test
    void englishCvGetsEnglishGeneratedTexts() {
        String cv = """
                John Smith

                SUMMARY
                Backend engineer.

                EXPERIENCE
                Engineer — Acme (2019 – present)
                - Built REST API in Java
                """;
        CandidateProfile p = new CandidateProfile();
        p.setSkills(List.of(new SkillEntry("Java", 4, 5)));
        TailorDraft d = tailor.tailor(cv, List.of(Requirement.must("Java")), p, TailoringOptions.defaults());
        assertThat(d.english()).isTrue();
        assertThat(d.text()).contains(CvTailor.FOCUS_EN + " Java (5 years).").contains("KEY SKILLS FOR THIS ROLE");
        assertThat(guard.check(cv, p, d.text()).passed()).isTrue();
    }

    @Test
    void polishYearsDeclension() {
        assertThat(CvTailor.lata(1)).isEqualTo("rok");
        assertThat(CvTailor.lata(3)).isEqualTo("lata");
        assertThat(CvTailor.lata(5)).isEqualTo("lat");
        assertThat(CvTailor.lata(12)).isEqualTo("lat");
        assertThat(CvTailor.lata(22)).isEqualTo("lata");
    }
}

package pl.radarpracy.tailoring;

import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.RequirementExtractor;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.SkillDictionary;

import java.lang.reflect.Field;
import java.util.List;

/** Dane demonstracyjne z {@code pl.radarpracy.demo.DemoTexts} (klasa pakietowa — odczyt przez refleksję). */
final class Demo {

    private Demo() {
    }

    static String text(String field) {
        try {
            Class<?> c = Class.forName("pl.radarpracy.demo.DemoTexts");
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Brak tekstu demo " + field, e);
        }
    }

    static String cvBackend() {
        return text("CV_BACKEND");
    }

    static String cvFullstack() {
        return text("CV_FULLSTACK");
    }

    static List<Requirement> requirements(String offerField) {
        return new RequirementExtractor(new SkillDictionary()).extract(text(offerField));
    }

    /** Profil jak w danych demo (Anna Nowak). */
    static CandidateProfile profile() {
        CandidateProfile p = new CandidateProfile();
        p.setFullName("Anna Nowak");
        p.setEmail("anna.nowak@example.com");
        p.setPhone("+48 600 100 200");
        p.setHeadline("Java Developer, 4 lata doświadczenia komercyjnego");
        p.setSkills(List.of(new SkillEntry("Java", 4, 4), new SkillEntry("Spring Boot", 4, 4),
                new SkillEntry("PostgreSQL", 3, 4), new SkillEntry("React", 3, 2), new SkillEntry("TypeScript", 3, 2),
                new SkillEntry("Docker", 3, 3), new SkillEntry("Kafka", 2, 1), new SkillEntry("Angielski", 4, null),
                new SkillEntry("Git", 4, 4)));
        return p;
    }
}

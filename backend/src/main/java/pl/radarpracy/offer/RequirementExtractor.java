package pl.radarpracy.offer;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wyodrębnia wymagania z treści ogłoszenia (MVP): sekcje „Wymagania” / „Mile widziane”
 * i ich punkty. Gdy ogłoszenie nie ma sekcji, wymaganiami stają się punkty zawierające
 * umiejętności ze słownika, a w ostateczności same wspomniane umiejętności.
 */
@Component
public class RequirementExtractor {

    private static final List<String> MUST_HEADERS = List.of(
            "wymagania", "wymagamy", "oczekujemy", "czego oczekujemy", "czego od ciebie oczekujemy", "nasze wymagania",
            "requirements", "must have", "must-have", "your profile", "twoj profil", "we expect", "what we expect",
            "kogo szukamy", "szukamy osoby", "who you are", "what you bring", "qualifications", "kwalifikacje",
            "niezbedne", "required", "oczekiwania", "nasze oczekiwania", "twoje kompetencje", "tech stack", "stack");
    private static final List<String> NICE_HEADERS = List.of(
            "mile widzian", "nice to have", "nice-to-have", "dodatkowym atutem", "dodatkowe atuty", "atutem bedzie",
            "bedzie plusem", "plusem bedzie", "bonus", "preferred", "optional", "dodatkowo docenimy", "docenimy");
    private static final List<String> OTHER_HEADERS = List.of(
            "oferujemy", "co oferujemy", "we offer", "benefity", "benefits", "obowiazki", "zakres obowiazkow",
            "twoje zadania", "responsibilities", "your tasks", "o nas", "about us", "about the role", "o firmie",
            "klauzula", "proces rekrutacji", "recruitment process", "what we offer", "zadania", "czym bedziesz",
            "your responsibilities", "your role", "twoja rola", "o projekcie", "about the project", "zapewniamy",
            "co zyskasz", "dlaczego warto", "why join", "perks", "wynagrodzenie", "salary", "lokalizacja");
    private static final List<String> NICE_INLINE = List.of(
            "mile widzian", "nice to have", "bedzie plusem", "atutem", "(plus)", "a plus", "as a plus", "dodatkowy atut",
            "preferowan", "is a plus", "docenimy");

    private enum Section { NONE, MUST, NICE, OTHER }

    private final SkillDictionary dictionary;

    public RequirementExtractor(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public List<Requirement> extract(String description) {
        List<Requirement> out = new ArrayList<>();
        if (Text.isBlank(description)) {
            return out;
        }
        Section section = Section.NONE;
        boolean sawRequirementSection = false;
        for (String line : Text.lines(description)) {
            Section header = header(line);
            if (header != null) {
                section = header;
                sawRequirementSection |= header == Section.MUST || header == Section.NICE;
                // „Wymagania: Java, Spring” — treść po dwukropku w tej samej linii
                int colon = line.indexOf(':');
                if (colon > 0 && colon < line.length() - 3 && (header == Section.MUST || header == Section.NICE)) {
                    addRequirement(out, line.substring(colon + 1), header == Section.MUST);
                }
                continue;
            }
            if (section == Section.MUST || section == Section.NICE) {
                addRequirement(out, line, section == Section.MUST);
            }
        }
        if (!sawRequirementSection) {
            for (String line : Text.lines(description)) {
                if (isBullet(line) && !dictionary.find(line).isEmpty()) {
                    addRequirement(out, line, true);
                }
            }
        }
        if (out.isEmpty()) {
            Set<String> skills = new LinkedHashSet<>(dictionary.skillsIn(description));
            for (String skill : skills) {
                out.add(Requirement.must(skill));
            }
        }
        return out;
    }

    private void addRequirement(List<Requirement> out, String rawLine, boolean mustSection) {
        String text = stripBullet(rawLine);
        if (text.length() < 2 || text.length() > 400) {
            return;
        }
        String folded = Text.fold(text);
        boolean must = mustSection && NICE_INLINE.stream().noneMatch(folded::contains);
        out.add(new Requirement(text, must, YearsParser.requiredYears(text)));
    }

    private Section header(String line) {
        String t = stripBullet(line);
        if (t.length() > 70) {
            return null;
        }
        boolean endsWithColon = t.endsWith(":");
        boolean bullet = isBullet(line);
        if (bullet && !endsWithColon) {
            return null;
        }
        String f = Text.fold(t).replaceAll("[:#*_]+", " ").strip();
        if (f.isEmpty()) {
            return null;
        }
        // nagłówek to krótka linia zaczynająca się od słowa kluczowego (lub zawierająca je, gdy kończy się dwukropkiem)
        for (String h : NICE_HEADERS) {
            if (f.startsWith(h) || (endsWithColon && f.contains(h))) {
                return Section.NICE;
            }
        }
        for (String h : MUST_HEADERS) {
            if (f.startsWith(h) || (endsWithColon && f.contains(h) && f.length() < 40)) {
                return Section.MUST;
            }
        }
        for (String h : OTHER_HEADERS) {
            if (f.startsWith(h) || (endsWithColon && f.contains(h) && f.length() < 40)) {
                return Section.OTHER;
            }
        }
        return null;
    }

    static boolean isBullet(String line) {
        String t = line.strip();
        return !t.isEmpty() && ("-•*·▪–—✓✔►>".indexOf(t.charAt(0)) >= 0 || t.matches("^\\d{1,2}[.)]\\s+.*"));
    }

    static String stripBullet(String line) {
        String t = line.strip();
        t = t.replaceFirst("^(?:[-•*·▪–—✓✔►>]+|\\d{1,2}[.)])\\s*", "");
        return t.strip();
    }
}

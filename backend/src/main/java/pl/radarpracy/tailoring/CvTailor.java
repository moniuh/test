package pl.radarpracy.tailoring;

import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;
import pl.radarpracy.tailoring.TailorDraft.BulletChange;
import pl.radarpracy.tailoring.TailorDraft.Line;
import pl.radarpracy.tailoring.TailorDraft.Origin;
import pl.radarpracy.tailoring.TailorDraft.RoleChange;
import pl.radarpracy.tailoring.TailorDraft.Section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generator dopasowanego CV — wersja offline, deterministyczna (pomysły #67 i #70).
 *
 * <ol>
 *   <li>blok nagłówka i kontaktu — bez zmian;</li>
 *   <li>podsumowanie pod ofertę z prawdziwych danych: nagłówek profilu, lata z profilu i umiejętności
 *       z wymagań, które kandydat ma (w CV albo w profilu); oryginalne podsumowanie zostaje;</li>
 *   <li>„Kluczowe kompetencje pod to stanowisko” — tylko posiadane umiejętności, w kolejności wag wymagań;</li>
 *   <li>punkty doświadczenia w każdym stanowisku posortowane wg trafności, opcjonalnie zwinięte nieistotne
 *       (stanowiska i daty nigdy nie znikają);</li>
 *   <li>sekcja umiejętności — pasujące pozycje na początku;</li>
 *   <li>pozostałe sekcje bez zmian.</li>
 * </ol>
 * <p>Wynik offline z definicji przechodzi strażnika prawdomówności: wszystkie linie są dosłowne,
 * przestawione albo zbudowane wyłącznie z faktów sprawdzonych tą samą kontrolą co strażnik.</p>
 */
@Component
public class CvTailor {

    /** Początek wygenerowanej linii podsumowania — pozwala ją rozpoznać przy ponownym dopasowaniu. */
    static final String FOCUS_PL = "Obszary kluczowe dla tej roli, w których mam doświadczenie:";
    static final String FOCUS_EN = "Key areas for this role in which I have experience:";
    static final String COMPETENCES_PL = "Kluczowe kompetencje pod to stanowisko";
    static final String COMPETENCES_EN = "Key skills for this role";
    private static final int FOCUS_MAX = 5;
    private static final Pattern LEVEL = Pattern.compile("(?<![\\p{L}\\d])[ABC][12](?![\\p{L}\\d])");
    private static final Pattern ITEM_SPLIT = Pattern.compile("\\s*[,;|•]\\s*");
    private static final Set<String> ENGLISH_HEADINGS = Set.of("summary", "professional summary", "profile",
            "experience", "work experience", "professional experience", "skills", "technical skills", "languages",
            "education", "projects", "certifications", "certificates", "employment history", "about me");

    private final SkillDictionary dictionary;
    private final TruthGuard guard;

    public CvTailor(SkillDictionary dictionary, TruthGuard guard) {
        this.dictionary = dictionary;
        this.guard = guard;
    }

    public TailorDraft tailor(String baseText, List<Requirement> requirements, CandidateProfile profile,
                              TailoringOptions options) {
        TailoringOptions opts = TailoringOptions.orDefaults(options);
        ParsedCv cv = CvStructureParser.parse(baseText);
        TruthGuard.Facts facts = guard.facts(baseText, profile);
        RelevanceScorer scorer = new RelevanceScorer(dictionary, requirements);
        boolean upper = cv.upperCaseHeadings();
        boolean english = isEnglish(cv);

        List<String> possessed = possessedSkills(scorer, facts);
        List<String> competences = competenceItems(possessed, cv);
        List<String> fromProfile = possessed.stream().filter(s -> !facts.cvSkills().contains(s)).toList();

        List<Section> out = new ArrayList<>();
        List<RoleChange> roleChanges = new ArrayList<>();
        boolean summaryDone = false;
        Section competencesSection = competences.isEmpty() ? null : new Section(CvSectionKind.KEY_COMPETENCES,
                heading(english ? COMPETENCES_EN : COMPETENCES_PL, upper),
                List.of(new Line(null, Origin.GENERATED, String.join(", ", competences), null, null)));

        for (ParsedCv.Section s : cv.sections()) {
            switch (s.kind()) {
                case HEADER -> {
                    out.add(new Section(s.kind(), null, s.lines().stream().map(Line::verbatim).toList()));
                    if (!hasSection(cv, CvSectionKind.SUMMARY)) {
                        List<Line> summary = summaryLines(List.of(), possessed, profile, facts, english);
                        if (!summary.isEmpty()) {
                            out.add(new Section(CvSectionKind.SUMMARY,
                                    heading(english ? "Summary" : "Podsumowanie", upper), summary));
                        }
                        summaryDone = true;
                        addIfPresent(out, competencesSection);
                    }
                }
                case SUMMARY -> {
                    if (summaryDone) {
                        out.add(verbatim(s));
                    } else {
                        out.add(new Section(s.kind(), s.heading(),
                                summaryLines(s.lines(), possessed, profile, facts, english)));
                        summaryDone = true;
                        addIfPresent(out, competencesSection);
                    }
                }
                case KEY_COMPETENCES -> {
                    // poprzednia sekcja z tailoringu — zastępuje ją nowa (wstawiona po podsumowaniu)
                }
                case EXPERIENCE -> out.add(experience(s, scorer, opts, roleChanges));
                case SKILLS -> out.add(new Section(s.kind(), s.heading(), skills(s.lines(), scorer)));
                default -> out.add(verbatim(s));
            }
        }
        return new TailorDraft(List.copyOf(out), competences, fromProfile, List.copyOf(roleChanges), english);
    }

    // --- podsumowanie i kompetencje ------------------------------------------------

    /** Posiadane umiejętności z wymagań (CV, profil, relacja „obejmuje”) w kolejności wag wymagań. */
    List<String> possessedSkills(RelevanceScorer scorer, TruthGuard.Facts facts) {
        Set<String> out = new LinkedHashSet<>();
        for (RelevanceScorer.Req r : scorer.byPriority()) {
            for (String skill : r.skills()) {
                Skill def = dictionary.get(skill).orElse(null);
                if (def != null && "wyksztalcenie".equals(def.category())) {
                    continue; // wykształcenie jest w osobnej sekcji CV
                }
                if (facts.allowedSkills().contains(skill)) {
                    out.add(skill);
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Pozycje kompetencji w brzmieniu z CV bazowego (np. „Apache Kafka”, „Mentoring” — nie „Zarządzanie
     * zespołem”), aby nazwa nie zawyżała faktu; dla języków z poziomem z CV (np. „Angielski (B2)”).
     * Umiejętności spoza tekstu CV (z profilu lub z relacji „obejmuje”) — nazwą ze słownika.
     */
    private List<String> competenceItems(List<String> possessed, ParsedCv cv) {
        Set<String> out = new LinkedHashSet<>();
        for (String skill : possessed) {
            Skill def = dictionary.get(skill).orElse(null);
            if (def != null && def.isLanguage()) {
                String level = languageLevel(cv, skill);
                out.add(level == null ? skill : skill + " (" + level + ")");
            } else {
                out.add(surfaceForm(cv, skill));
            }
        }
        return List.copyOf(out);
    }

    /** Pierwsze brzmienie umiejętności w CV: najpierw sekcja umiejętności, potem kompetencje, potem reszta. */
    private String surfaceForm(ParsedCv cv, String skill) {
        List<CvSectionKind> order = List.of(CvSectionKind.SKILLS, CvSectionKind.KEY_COMPETENCES,
                CvSectionKind.SUMMARY, CvSectionKind.EXPERIENCE, CvSectionKind.PROJECTS, CvSectionKind.CERTIFICATES,
                CvSectionKind.OTHER);
        for (CvSectionKind kind : order) {
            for (ParsedCv.Section s : cv.sections()) {
                if (s.kind() != kind) {
                    continue;
                }
                for (String line : s.lines()) {
                    for (var hit : dictionary.find(line)) {
                        if (hit.skill().equals(skill)) {
                            String surface = line.substring(hit.start(), hit.end()).strip();
                            return surface.isEmpty() ? skill
                                    : surface.substring(0, 1).toUpperCase(Locale.ROOT) + surface.substring(1);
                        }
                    }
                }
            }
        }
        return skill;
    }

    private String languageLevel(ParsedCv cv, String language) {
        for (ParsedCv.Section s : cv.sections()) {
            for (String line : s.lines()) {
                if (dictionary.skillsIn(line).contains(language)) {
                    Matcher m = LEVEL.matcher(line);
                    if (m.find()) {
                        return m.group();
                    }
                }
            }
        }
        return null;
    }

    private List<Line> summaryLines(List<String> original, List<String> possessed, CandidateProfile profile,
                                    TruthGuard.Facts facts, boolean english) {
        List<Line> out = new ArrayList<>();
        List<String> kept = original.stream()
                .filter(l -> !l.strip().startsWith(FOCUS_PL) && !l.strip().startsWith(FOCUS_EN)).toList();
        String keptText = String.join(" ", kept);
        String headline = profile == null ? null : profile.getHeadline();
        if (!english && !Text.isBlank(headline) && guard.factCheck(facts, headline).isEmpty()
                && (kept.isEmpty() || overlap(headline, keptText) < 0.6)) {
            String h = headline.strip();
            out.add(new Line(null, Origin.GENERATED, h.endsWith(".") ? h : h + ".", null, null));
        }
        String focus = focusLine(possessed, profile, english);
        if (focus != null && guard.factCheck(facts, focus).isEmpty()) {
            out.add(new Line(null, Origin.GENERATED, focus, null, null));
        }
        for (String l : trimBlank(kept)) {
            out.add(Line.verbatim(l));
        }
        return out;
    }

    /** „Obszary kluczowe dla tej roli…: Java (4 lata), Spring Boot (4 lata) i Kafka (1 rok).” */
    String focusLine(List<String> possessed, CandidateProfile profile, boolean english) {
        Map<String, Integer> years = new HashMap<>();
        if (profile != null) {
            for (SkillEntry s : profile.getSkills()) {
                if (s != null && s.name() != null && s.years() != null && s.years() > 0) {
                    years.put(dictionary.canonicalName(s.name()), s.years());
                }
            }
        }
        List<String> items = new ArrayList<>();
        for (String skill : possessed) {
            Skill def = dictionary.get(skill).orElse(null);
            if (def != null && (def.isLanguage() || "miekkie".equals(def.category()))) {
                continue;
            }
            Integer y = years.get(skill);
            items.add(y == null ? skill : skill + " (" + y + " " + (english ? (y == 1 ? "year" : "years") : lata(y))
                    + ")");
            if (items.size() == FOCUS_MAX) {
                break;
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        String list = items.size() == 1 ? items.get(0)
                : String.join(", ", items.subList(0, items.size() - 1)) + (english ? " and " : " i ")
                + items.get(items.size() - 1);
        return (english ? FOCUS_EN : FOCUS_PL) + " " + list + ".";
    }

    // --- doświadczenie (#70) --------------------------------------------------------

    private Section experience(ParsedCv.Section s, RelevanceScorer scorer, TailoringOptions opts,
                               List<RoleChange> changes) {
        List<Line> lines = new ArrayList<>();
        List<ParsedCv.Role> roles = s.roles();
        int roleIndex = changes.size();
        for (int r = 0; r < roles.size(); r++) {
            ParsedCv.Role role = roles.get(r);
            if (r > 0) {
                lines.add(Line.blank());
            }
            if (role.header() != null) {
                lines.add(Line.verbatim(role.header()));
            }
            role.details().forEach(d -> lines.add(Line.verbatim(d)));

            List<ParsedCv.Bullet> bullets = role.bullets();
            List<Integer> order = new ArrayList<>();
            double[] score = new double[bullets.size()];
            for (int b = 0; b < bullets.size(); b++) {
                order.add(b);
                score[b] = scorer.score(bullets.get(b).text());
            }
            order.sort(Comparator.comparingDouble((Integer b) -> -score[b]).thenComparingInt(b -> b));
            Set<Integer> hidden = new HashSet<>();
            for (int pos = 0; pos < order.size(); pos++) {
                int b = order.get(pos);
                boolean irrelevant = opts.hide() && score[b] == 0;
                boolean overLimit = opts.limit() > 0 && pos >= opts.limit();
                if ((irrelevant || overLimit) && pos > 0) {
                    hidden.add(b);
                }
            }
            List<BulletChange> bulletChanges = new ArrayList<>(java.util.Collections.nCopies(bullets.size(), null));
            int newPos = 0;
            for (int b : order) {
                ParsedCv.Bullet bullet = bullets.get(b);
                List<String> matched = scorer.skillsIn(bullet.text()).stream()
                        .filter(sk -> scorer.byPriority().stream().anyMatch(q -> q.skills().contains(sk))).toList();
                if (hidden.contains(b)) {
                    bulletChanges.set(b, new BulletChange(bullet.text(), b + 1, null, score[b], true, matched));
                    continue;
                }
                newPos++;
                bulletChanges.set(b, new BulletChange(bullet.text(), b + 1, newPos, score[b], false, matched));
                lines.add(new Line("r" + (roleIndex + r) + "b" + b, Origin.BULLET, String.join("\n", bullet.lines()),
                        bullet.text(), bullet.marker()));
            }
            changes.add(new RoleChange(role.header(), List.copyOf(bulletChanges)));
        }
        return new Section(s.kind(), s.heading(), List.copyOf(lines));
    }

    // --- umiejętności ---------------------------------------------------------------

    /** Pozycje list umiejętności: pasujące do wymagań na początku (stabilnie), reszta w kolejności z CV. */
    List<Line> skills(List<String> lines, RelevanceScorer scorer) {
        record Parsed(String raw, String marker, String label, List<String> items, boolean dot, int priority) {
        }
        List<Parsed> parsed = new ArrayList<>();
        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            String content = CvStructureParser.stripBullet(raw);
            String marker = raw.substring(0, raw.length() - content.length());
            String label = null;
            int colon = content.indexOf(':');
            if (colon > 0 && colon < 40) {
                label = content.substring(0, colon + 1);
                content = content.substring(colon + 1);
            }
            String trimmed = content.strip();
            boolean dot = trimmed.endsWith(".");
            if (dot) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            List<String> items = new ArrayList<>();
            for (String it : ITEM_SPLIT.split(trimmed)) {
                if (!it.isBlank()) {
                    items.add(it.strip());
                }
            }
            // przestawiamy tylko listy („Java, Spring Boot, Docker”), nie zdania z przecinkami
            boolean listLike = items.stream().allMatch(i -> i.split("\\s+").length <= 4);
            List<String> sorted = new ArrayList<>(items);
            if (listLike) {
                sorted.sort(Comparator.comparingInt(scorer::bestPriority));
            }
            int priority = listLike
                    ? sorted.stream().mapToInt(scorer::bestPriority).min().orElse(Integer.MAX_VALUE)
                    : scorer.bestPriority(raw);
            parsed.add(new Parsed(raw, marker, label, sorted, dot, priority));
        }
        List<Parsed> ordered = new ArrayList<>(parsed);
        ordered.sort(Comparator.comparingInt(Parsed::priority));
        List<Line> out = new ArrayList<>();
        for (Parsed p : ordered) {
            String rebuilt = p.marker() + (p.label() == null ? "" : p.label() + " ") + String.join(", ", p.items())
                    + (p.dot() ? "." : "");
            if (TruthGuard.norm(rebuilt).equals(TruthGuard.norm(p.raw()))) {
                out.add(Line.verbatim(p.raw()));
            } else {
                out.add(new Line(null, Origin.REORDERED, rebuilt, null, null));
            }
        }
        return out;
    }

    // --- pomocnicze -----------------------------------------------------------------

    private static Section verbatim(ParsedCv.Section s) {
        return new Section(s.kind(), s.heading(), s.lines().stream().map(Line::verbatim).toList());
    }

    private static boolean hasSection(ParsedCv cv, CvSectionKind kind) {
        return cv.sections().stream().anyMatch(s -> s.kind() == kind);
    }

    private static void addIfPresent(List<Section> out, Section s) {
        if (s != null) {
            out.add(s);
        }
    }

    private static String heading(String text, boolean upper) {
        return upper ? text.toUpperCase(Locale.forLanguageTag("pl")) : text;
    }

    static boolean isEnglish(ParsedCv cv) {
        List<String> headings = cv.sections().stream().map(ParsedCv.Section::heading).filter(h -> h != null)
                .map(h -> Text.fold(h).replaceAll("[^a-z ]", "").strip()).toList();
        return !headings.isEmpty() && headings.stream().filter(ENGLISH_HEADINGS::contains).count() * 2
                > headings.size();
    }

    private static List<String> trimBlank(List<String> lines) {
        int from = 0;
        int to = lines.size();
        while (from < to && lines.get(from).isBlank()) {
            from++;
        }
        while (to > from && lines.get(to - 1).isBlank()) {
            to--;
        }
        return lines.subList(from, to);
    }

    private static double overlap(String a, String b) {
        List<String> sa = RelevanceScorer.stems(a);
        Set<String> sb = new HashSet<>(RelevanceScorer.stems(b));
        if (sa.isEmpty()) {
            return 1;
        }
        return (double) sa.stream().filter(sb::contains).count() / sa.size();
    }

    /** Polska odmiana: 1 rok, 2–4 lata, 5+ lat (12–14 lat). */
    static String lata(int n) {
        if (n == 1) {
            return "rok";
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "lata" : "lat";
    }
}

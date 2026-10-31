package pl.radarpracy.tailoring;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Trafność fragmentu CV względem wymagań oferty (pomysł #70).
 *
 * <p>Każde wymaganie ma wagę jak w {@code MatchService} (obowiązkowe 3, mile widziane 1). Fragment
 * zdobywa wagę wymagania pomnożoną przez pokrycie: dla wymagań z umiejętnościami ze słownika —
 * udział wspomnianych umiejętności (z relacją „obejmuje”), dla pozostałych — udział wspólnych
 * rdzeni słów kluczowych (od 34%).</p>
 */
public class RelevanceScorer {

    static final int MUST_WEIGHT = 3;
    static final int NICE_WEIGHT = 1;

    /** Rdzenie słów zbyt ogólnych, by świadczyć o trafności („doświadczenie”, „znajomość”…). */
    private static final Set<String> GENERIC_STEMS = Set.of("doswi", "znajo", "umiej", "minim", "exper", "komer",
            "comme", "pozio", "dobra", "prakt", "wiedz", "wymag", "requi", "years", "praca", "pracy", "biegl",
            "fluen", "level", "produ", "solid", "stron", "good", "zespo", "mozli");

    /** Wymaganie przygotowane do oceny. */
    record Req(int index, String text, int weight, boolean anyOf, List<String> skills, List<String> stems) {
    }

    private final SkillDictionary dictionary;
    private final List<Req> requirements;

    public RelevanceScorer(SkillDictionary dictionary, List<Requirement> requirements) {
        this.dictionary = dictionary;
        List<Req> list = new ArrayList<>();
        List<Requirement> reqs = requirements == null ? List.of() : requirements;
        for (int i = 0; i < reqs.size(); i++) {
            Requirement r = reqs.get(i);
            String folded = Text.fold(r.text());
            boolean anyOf = folded.contains(" lub ") || folded.contains(" albo ") || folded.contains(" or ")
                    || folded.contains("/");
            list.add(new Req(i, r.text(), r.mustHave() ? MUST_WEIGHT : NICE_WEIGHT, anyOf,
                    List.copyOf(dictionary.skillsIn(r.text())), stems(r.text())));
        }
        this.requirements = List.copyOf(list);
    }

    /** Wymagania w kolejności ważności: obowiązkowe przed mile widzianymi, potem kolejność w ofercie. */
    public List<Req> byPriority() {
        return requirements.stream()
                .sorted(Comparator.comparingInt((Req r) -> -r.weight()).thenComparingInt(Req::index))
                .toList();
    }

    /** Umiejętności wspomniane we fragmencie (z umiejętnościami, które obejmują). */
    public Set<String> skillsIn(String text) {
        Set<String> out = new LinkedHashSet<>(dictionary.skillsIn(text));
        for (String s : List.copyOf(out)) {
            out.addAll(dictionary.impliedBy(s));
        }
        return out;
    }

    /** Punkty trafności fragmentu (0 = nie dotyczy żadnego wymagania). */
    public double score(String text) {
        Set<String> skills = skillsIn(text);
        Set<String> lineStems = new LinkedHashSet<>(stems(text));
        double total = 0;
        for (Req r : requirements) {
            total += r.weight() * coverage(r, skills, lineStems);
        }
        return Math.round(total * 100.0) / 100.0;
    }

    /** Najlepsza pozycja wymagania (wg priorytetu), którego dotyczy fragment; MAX_VALUE, gdy żadnego. */
    public int bestPriority(String text) {
        Set<String> skills = skillsIn(text);
        Set<String> lineStems = new LinkedHashSet<>(stems(text));
        List<Req> ordered = byPriority();
        for (int i = 0; i < ordered.size(); i++) {
            if (coverage(ordered.get(i), skills, lineStems) > 0) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static double coverage(Req r, Set<String> skills, Set<String> lineStems) {
        if (!r.skills().isEmpty()) {
            long hit = r.skills().stream().filter(skills::contains).count();
            if (hit == 0) {
                return 0;
            }
            return r.anyOf() ? 1.0 : (double) hit / r.skills().size();
        }
        if (r.stems().isEmpty()) {
            return 0;
        }
        long common = r.stems().stream().filter(lineStems::contains).count();
        double ratio = (double) common / r.stems().size();
        return ratio >= 0.34 ? ratio : 0;
    }

    static List<String> stems(String text) {
        List<String> out = new ArrayList<>();
        for (String t : Text.tokens(text, 4)) {
            String s = Text.stem(t, 5);
            if (!GENERIC_STEMS.contains(s) && !out.contains(s) && !s.chars().allMatch(Character::isDigit)) {
                out.add(s);
            }
        }
        return out;
    }
}

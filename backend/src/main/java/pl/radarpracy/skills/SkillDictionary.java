package pl.radarpracy.skills;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.radarpracy.common.Text;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Słownik umiejętności z synonimami, rodzinami i relacją „obejmuje” (pomysł #28).
 *
 * <p>Wyszukiwanie działa na tekście złożonym (małe litery, bez polskich znaków) z kontrolą granic
 * słów; przy nakładających się dopasowaniach wygrywa najdłuższy alias („React Native” zamiast
 * „React”). Słownik jest ładowany z {@code classpath:skills/slownik.json}.</p>
 */
@Component
public class SkillDictionary {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String name, String category, String family, List<String> aliases, List<String> implies,
                 Boolean prefix) {
    }

    private record Alias(String folded, Skill skill) {
    }

    private final Map<String, Skill> byName = new LinkedHashMap<>();
    private final Map<String, Skill> byFoldedName = new HashMap<>();
    private final List<Alias> aliases = new ArrayList<>();

    public SkillDictionary() {
        this(load("skills/slownik.json"));
    }

    SkillDictionary(List<Entry> entries) {
        for (Entry e : entries) {
            List<String> folded = new ArrayList<>();
            for (String a : e.aliases()) {
                folded.add(Text.fold(a).strip());
            }
            String foldedName = Text.fold(e.name());
            if (!folded.contains(foldedName) && !Boolean.TRUE.equals(e.prefix())) {
                folded.add(foldedName);
            }
            Skill skill = new Skill(e.name(), e.category(), e.family(), List.copyOf(folded),
                    e.implies() == null ? List.of() : List.copyOf(e.implies()), Boolean.TRUE.equals(e.prefix()));
            byName.put(skill.name(), skill);
            byFoldedName.put(foldedName, skill);
            for (String a : folded) {
                aliases.add(new Alias(a, skill));
                byFoldedName.putIfAbsent(a, skill);
            }
        }
        // dłuższe aliasy najpierw — ułatwia wybór najdłuższego dopasowania
        aliases.sort(Comparator.comparingInt((Alias a) -> a.folded().length()).reversed());
    }

    private static List<Entry> load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Nie można wczytać słownika umiejętności " + path, e);
        }
    }

    public Collection<Skill> all() {
        return byName.values();
    }

    public Optional<Skill> get(String canonicalName) {
        return Optional.ofNullable(byName.get(canonicalName));
    }

    /** Rozpoznaje nazwę lub alias (np. „k8s”, „postgres”) i zwraca umiejętność kanoniczną. */
    public Optional<Skill> resolve(String nameOrAlias) {
        if (nameOrAlias == null) {
            return Optional.empty();
        }
        String f = Text.fold(nameOrAlias).strip();
        Skill direct = byFoldedName.get(f);
        if (direct != null) {
            return Optional.of(direct);
        }
        List<SkillHit> hits = find(nameOrAlias);
        if (hits.size() == 1 && hits.get(0).end() - hits.get(0).start() >= f.length() - 2) {
            return get(hits.get(0).skill());
        }
        return Optional.empty();
    }

    /** Nazwa kanoniczna, jeśli znana; w przeciwnym razie oryginalny zapis. */
    public String canonicalName(String nameOrAlias) {
        return resolve(nameOrAlias).map(Skill::name).orElse(nameOrAlias == null ? null : nameOrAlias.strip());
    }

    /** Wszystkie wystąpienia umiejętności w tekście (bez nakładania się), w kolejności występowania. */
    public List<SkillHit> find(String text) {
        List<SkillHit> hits = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return hits;
        }
        String folded = Text.fold(text);
        boolean[] taken = new boolean[folded.length()];
        for (Alias alias : aliases) {
            String a = alias.folded();
            if (a.isEmpty()) {
                continue;
            }
            int from = 0;
            while (true) {
                int idx = folded.indexOf(a, from);
                if (idx < 0) {
                    break;
                }
                int end = idx + a.length();
                if (isWordStart(folded, idx, a) && (alias.skill().prefix() || isWordEnd(folded, end, a))
                        && !overlaps(taken, idx, end)) {
                    int realEnd = alias.skill().prefix() ? extendWord(folded, end) : end;
                    for (int i = idx; i < realEnd; i++) {
                        taken[i] = true;
                    }
                    hits.add(new SkillHit(alias.skill().name(), idx, realEnd, a));
                }
                from = idx + 1;
            }
        }
        hits.sort(Comparator.comparingInt(SkillHit::start));
        return hits;
    }

    /** Zbiór nazw kanonicznych wspomnianych w tekście (z zachowaniem kolejności). */
    public Set<String> skillsIn(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (SkillHit h : find(text)) {
            out.add(h.skill());
        }
        return out;
    }

    /** Umiejętności obejmowane przez daną (rekurencyjnie, bez niej samej). */
    public Set<String> impliedBy(String skillName) {
        Set<String> out = new LinkedHashSet<>();
        collectImplied(skillName, out);
        out.remove(skillName);
        return out;
    }

    private void collectImplied(String name, Set<String> acc) {
        Skill s = byName.get(name);
        if (s == null) {
            return;
        }
        for (String i : s.implies()) {
            if (acc.add(i)) {
                collectImplied(i, acc);
            }
        }
    }

    public boolean sameFamily(String a, String b) {
        Skill sa = byName.get(a);
        Skill sb = byName.get(b);
        return sa != null && sb != null && sa.family() != null && sa.family().equals(sb.family()) && !a.equals(b);
    }

    private static boolean isWordStart(String s, int idx, String alias) {
        if (idx == 0) {
            return true;
        }
        char prev = s.charAt(idx - 1);
        if (Character.isLetterOrDigit(prev)) {
            return false;
        }
        // „.net” poprzedzone literą (asp.net) obsłuży dłuższy alias; „node.js” nie powinno pasować do „js”
        return !(prev == '.' && Character.isLetterOrDigit(alias.charAt(0)));
    }

    private static boolean isWordEnd(String s, int end, String alias) {
        if (end >= s.length()) {
            return true;
        }
        char next = s.charAt(end);
        if (Character.isLetterOrDigit(next)) {
            return false;
        }
        char last = alias.charAt(alias.length() - 1);
        // „c” nie może pasować do „c++” ani „c#”
        if (Character.isLetterOrDigit(last) && (next == '+' || next == '#')) {
            return false;
        }
        // „node” nie może pasować do „node.js” (dłuższy alias już zajął miejsce), ale „java.” na końcu zdania tak
        return !(next == '.' && end + 1 < s.length() && Character.isLetterOrDigit(s.charAt(end + 1)));
    }

    private static int extendWord(String s, int end) {
        int i = end;
        while (i < s.length() && Character.isLetter(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean overlaps(boolean[] taken, int from, int to) {
        for (int i = from; i < to; i++) {
            if (taken[i]) {
                return true;
            }
        }
        return false;
    }
}

package pl.radarpracy.application.snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proste porównanie dwóch wersji tekstu linia po linii (pomysł #46): ile linii dodano i usunięto.
 * Porównujemy multizbiory niepustych, przyciętych linii — kolejność i białe znaki nie mają znaczenia.
 */
public final class TextDiff {

    /** Wynik porównania. {@code same} = brak różnic merytorycznych. */
    public record Result(boolean same, int added, int removed) {
    }

    private TextDiff() {
    }

    public static Result compare(String before, String after) {
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines(before)) {
            counts.merge(line, 1, Integer::sum);
        }
        int added = 0;
        for (String line : lines(after)) {
            Integer c = counts.get(line);
            if (c == null || c == 0) {
                added++;
            } else {
                counts.put(line, c - 1);
            }
        }
        int removed = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new Result(added == 0 && removed == 0, added, removed);
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        for (String line : s.split("\\R")) {
            String t = line.strip().replaceAll("\\s+", " ");
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}

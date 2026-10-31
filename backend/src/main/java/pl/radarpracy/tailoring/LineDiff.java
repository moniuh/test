package pl.radarpracy.tailoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Różnice liniowe między CV bazowym a dopasowanym: linie dodane, przeniesione, zmienione
 * (np. nowa kolejność pozycji), ukryte i bez zmian. Puste linie są pomijane.
 */
public final class LineDiff {

    /** Rodzaj zmiany linii. */
    public enum Kind {
        /** bez zmian, w tej samej kolejności */
        SAME,
        /** nowa linia (np. wygenerowane podsumowanie, kluczowe kompetencje) */
        ADDED,
        /** ta sama linia w innym miejscu (np. punkt doświadczenia wyżej) */
        MOVED,
        /** linia zmieniona — np. inna kolejność umiejętności albo przeredagowany punkt */
        CHANGED,
        /** linia z CV bazowego, której nie ma w wersji dopasowanej (zwinięty punkt) */
        REMOVED
    }

    /**
     * Linia różnic.
     *
     * @param kind     rodzaj
     * @param text     treść (dla REMOVED — linia z CV bazowego)
     * @param previous poprzednia treść dla CHANGED
     * @param baseLine numer linii w CV bazowym (od 1) lub null
     * @param newLine  numer linii w wersji dopasowanej (od 1) lub null
     */
    public record Entry(Kind kind, String text, String previous, Integer baseLine, Integer newLine) {
    }

    private record Numbered(String text, int no) {
    }

    private LineDiff() {
    }

    public static List<Entry> diff(String base, String tailored) {
        List<Numbered> a = numbered(base);
        List<Numbered> b = numbered(tailored);
        int n = a.size();
        int m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = key(a.get(i)).equals(key(b.get(j))) ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        // linie poza LCS: wspólne dla obu stron (wielozbiór) to przeniesienia
        Map<String, Integer> removedPool = new HashMap<>();
        Map<String, Integer> addedPool = new HashMap<>();
        List<Entry> raw = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && key(a.get(i)).equals(key(b.get(j)))) {
                raw.add(new Entry(Kind.SAME, b.get(j).text(), null, a.get(i).no(), b.get(j).no()));
                i++;
                j++;
            } else if (j < m && (i >= n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                raw.add(new Entry(Kind.ADDED, b.get(j).text(), null, null, b.get(j).no()));
                addedPool.merge(key(b.get(j)), 1, Integer::sum);
                j++;
            } else {
                raw.add(new Entry(Kind.REMOVED, a.get(i).text(), null, a.get(i).no(), null));
                removedPool.merge(key(a.get(i)), 1, Integer::sum);
                i++;
            }
        }
        Map<String, Integer> moved = new HashMap<>();
        for (var e : addedPool.entrySet()) {
            int count = Math.min(e.getValue(), removedPool.getOrDefault(e.getKey(), 0));
            if (count > 0) {
                moved.put(e.getKey(), count);
            }
        }
        Map<String, Integer> movedAdded = new HashMap<>(moved);
        Map<String, Integer> movedRemoved = new HashMap<>(moved);
        Map<String, Integer> baseNo = new HashMap<>();
        for (Entry e : raw) {
            if (e.kind() == Kind.REMOVED) {
                baseNo.putIfAbsent(TruthGuard.norm(e.text()), e.baseLine());
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : raw) {
            String k = TruthGuard.norm(e.text());
            if (e.kind() == Kind.ADDED && movedAdded.getOrDefault(k, 0) > 0) {
                movedAdded.merge(k, -1, Integer::sum);
                out.add(new Entry(Kind.MOVED, e.text(), null, baseNo.get(k), e.newLine()));
            } else if (e.kind() == Kind.REMOVED && movedRemoved.getOrDefault(k, 0) > 0) {
                movedRemoved.merge(k, -1, Integer::sum);
            } else {
                out.add(e);
            }
        }
        return pairChanged(out);
    }

    /** Linia dodana o tych samych słowach co usunięta (np. inna kolejność pozycji) to zmiana, nie dwie linie. */
    private static List<Entry> pairChanged(List<Entry> entries) {
        List<Entry> out = new ArrayList<>(entries);
        Set<Integer> consumed = new HashSet<>();
        for (int x = 0; x < out.size(); x++) {
            Entry add = out.get(x);
            if (add.kind() != Kind.ADDED) {
                continue;
            }
            Set<String> words = words(add.text());
            int best = -1;
            double bestScore = 0;
            for (int y = 0; y < out.size(); y++) {
                Entry rem = out.get(y);
                if (rem.kind() != Kind.REMOVED || consumed.contains(y)) {
                    continue;
                }
                double score = jaccard(words, words(rem.text()));
                if (score > bestScore) {
                    bestScore = score;
                    best = y;
                }
            }
            if (best >= 0 && bestScore >= 0.5) {
                Entry rem = out.get(best);
                consumed.add(best);
                out.set(x, new Entry(Kind.CHANGED, add.text(), rem.text(), rem.baseLine(), add.newLine()));
            }
        }
        List<Entry> result = new ArrayList<>();
        for (int x = 0; x < out.size(); x++) {
            if (!consumed.contains(x)) {
                result.add(out.get(x));
            }
        }
        return result;
    }

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        for (String t : pl.radarpracy.common.Text.tokens(s, 2)) {
            out.add(t.replaceAll("[.,;]+$", ""));
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static String key(Numbered n) {
        return TruthGuard.norm(n.text());
    }

    private static List<Numbered> numbered(String text) {
        List<Numbered> out = new ArrayList<>();
        String[] lines = (text == null ? "" : text).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                out.add(new Numbered(lines[i].stripTrailing(), i + 1));
            }
        }
        return out;
    }
}

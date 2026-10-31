package pl.radarpracy.application;

import java.util.ArrayList;
import java.util.List;

/** Kolejność kart w kolumnie tablicy Kanban (pomysł #39) — czysta logika. */
public final class BoardOrder {

    private BoardOrder() {
    }

    /**
     * Wstawia element na wskazaną pozycję kolumny (po usunięciu go z dotychczasowego miejsca).
     * Pozycja spoza zakresu jest przycinana; {@code null} oznacza koniec kolumny.
     */
    public static <T> List<T> insert(List<T> column, T item, Integer position) {
        List<T> out = new ArrayList<>(column);
        out.remove(item);
        int idx = position == null ? out.size() : Math.max(0, Math.min(position, out.size()));
        out.add(idx, item);
        return out;
    }
}

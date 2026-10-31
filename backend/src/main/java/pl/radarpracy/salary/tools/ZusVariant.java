package pl.radarpracy.salary.tools;

/** Wariant składek społecznych przedsiębiorcy. */
public enum ZusVariant {
    /** pełny („duży”) ZUS od podstawy 60% prognozowanego przeciętnego wynagrodzenia */
    FULL,
    /** preferencyjny ZUS (pierwsze 24 miesiące działalności) od 30% płacy minimalnej */
    PREFERENTIAL,
    /** ulga na start (pierwsze 6 miesięcy) — bez składek społecznych, tylko zdrowotna */
    START_RELIEF
}

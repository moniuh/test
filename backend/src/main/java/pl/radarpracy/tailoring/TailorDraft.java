package pl.radarpracy.tailoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dopasowana wersja CV w postaci sekcji i linii (przed złożeniem w tekst). Pozwala podmienić
 * przeredagowane przez AI podsumowanie i punkty, nie ruszając reszty.
 *
 * @param sections          sekcje wynikowe w kolejności
 * @param competences       pozycje sekcji „Kluczowe kompetencje pod to stanowisko”
 * @param addedFromProfile  umiejętności dopisane z profilu (nie było ich w CV bazowym)
 * @param roles             zmiany kolejności i ukrycia punktów w stanowiskach (#70)
 * @param english           czy CV bazowe jest po angielsku (nowe teksty w tym języku)
 */
public record TailorDraft(List<Section> sections, List<String> competences, List<String> addedFromProfile,
                          List<RoleChange> roles, boolean english) {

    /** Pochodzenie linii wynikowej. */
    public enum Origin { VERBATIM, GENERATED, REORDERED, BULLET, BLANK }

    /**
     * Linia wynikowa.
     *
     * @param id      identyfikator punktu dla AI (np. „r0b2”) lub null
     * @param origin  pochodzenie
     * @param text    tekst do wstawienia (dla punktów — dosłowne linie z punktorem, rozdzielone \n)
     * @param content treść punktu bez punktora (dla AI) lub null
     * @param marker  punktor z wcięciem (dla punktów) lub null
     */
    public record Line(String id, Origin origin, String text, String content, String marker) {

        static Line verbatim(String text) {
            return new Line(null, text.isBlank() ? Origin.BLANK : Origin.VERBATIM, text, null, null);
        }

        static Line blank() {
            return new Line(null, Origin.BLANK, "", null, null);
        }
    }

    /** Sekcja wynikowa: nagłówek (dosłownie lub nowy) i linie. */
    public record Section(CvSectionKind kind, String heading, List<Line> lines) {
    }

    /** Punkt doświadczenia: pozycja przed i po, trafność i powód. */
    public record BulletChange(String text, int originalPosition, Integer newPosition, double relevance,
                               boolean hidden, List<String> matchedSkills) {
    }

    /** Stanowisko z listą punktów (#70). */
    public record RoleChange(String header, List<BulletChange> bullets) {

        public boolean reordered() {
            return bullets.stream().anyMatch(b -> b.newPosition() != null && b.newPosition() != b.originalPosition());
        }
    }

    /** Linie podsumowania (bez nagłówka) — do przeredagowania przez AI. */
    public List<String> summaryLines() {
        return sections.stream().filter(s -> s.kind() == CvSectionKind.SUMMARY).findFirst()
                .map(s -> s.lines().stream().filter(l -> l.origin() != Origin.BLANK).map(Line::text).toList())
                .orElse(List.of());
    }

    /** Widoczne punkty doświadczenia (id → treść bez punktora). */
    public List<Line> bullets() {
        return sections.stream().flatMap(s -> s.lines().stream()).filter(l -> l.origin() == Origin.BULLET).toList();
    }

    /** Złożony tekst CV. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            if (s.heading() == null && s.lines().isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            List<String> out = new ArrayList<>();
            if (s.heading() != null) {
                out.add(s.heading());
            }
            for (Line l : s.lines()) {
                out.add(l.text());
            }
            sb.append(String.join("\n", out));
        }
        return sb.append('\n').toString();
    }

    /**
     * Kopia z podmienionym podsumowaniem i treścią punktów (punktor zostaje). Puste/nieobecne wpisy
     * pozostawiają wersję offline.
     */
    public TailorDraft withRewrites(List<String> summary, Map<String, String> bullets) {
        List<Section> out = new ArrayList<>();
        for (Section s : sections) {
            List<Line> lines = new ArrayList<>();
            if (s.kind() == CvSectionKind.SUMMARY && summary != null && !summary.isEmpty()) {
                for (String l : summary) {
                    lines.add(new Line(null, Origin.GENERATED, l, null, null));
                }
            } else {
                for (Line l : s.lines()) {
                    String rewrite = l.id() == null ? null : bullets.get(l.id());
                    if (l.origin() == Origin.BULLET && rewrite != null && !rewrite.isBlank()) {
                        lines.add(new Line(l.id(), Origin.BULLET, l.marker() + rewrite.strip(), rewrite.strip(),
                                l.marker()));
                    } else {
                        lines.add(l);
                    }
                }
            }
            out.add(new Section(s.kind(), s.heading(), List.copyOf(lines)));
        }
        return new TailorDraft(List.copyOf(out), competences, addedFromProfile, roles, english);
    }
}

package pl.radarpracy.tailoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Struktura CV odczytana z czystego tekstu. Wszystkie linie są dosłownymi liniami źródła
 * (bez zmian w treści), dzięki czemu sekcje można przestawiać bez ryzyka zmiany faktów.
 *
 * @param sections sekcje w kolejności występowania; pierwsza to zawsze {@link CvSectionKind#HEADER}
 */
public record ParsedCv(List<Section> sections) {

    /**
     * Sekcja CV.
     *
     * @param kind    rodzaj
     * @param heading linia nagłówka (dosłownie) lub null dla bloku nagłówka CV
     * @param lines   linie treści (dosłownie, bez pustych na początku i końcu)
     * @param roles   stanowiska — tylko dla sekcji doświadczenia (w pozostałych pusta lista)
     */
    public record Section(CvSectionKind kind, String heading, List<String> lines, List<Role> roles) {

        /** Linie treści bez pustych. */
        public List<String> nonBlankLines() {
            return lines.stream().filter(l -> !l.isBlank()).toList();
        }
    }

    /**
     * Stanowisko w sekcji doświadczenia.
     *
     * @param header       pierwsza linia stanowiska (zwykle rola, pracodawca i daty) lub null, gdy punkty
     *                     pojawiły się bez nagłówka
     * @param details      kolejne linie przed punktami (np. osobna linia z datami lub opis)
     * @param bullets      punkty doświadczenia
     */
    public record Role(String header, List<String> details, List<Bullet> bullets) {

        /** Linie identyfikujące stanowisko: nagłówek i linie szczegółów z datami. */
        public List<String> identityLines() {
            List<String> out = new ArrayList<>();
            if (header != null) {
                out.add(header.strip());
            }
            for (String d : details) {
                if (CvStructureParser.hasDate(d)) {
                    out.add(d.strip());
                }
            }
            return out;
        }
    }

    /**
     * Punkt doświadczenia.
     *
     * @param lines linie punktu dosłownie (pierwsza z punktorem, kolejne — kontynuacje zawiniętego tekstu)
     */
    public record Bullet(List<String> lines) {

        /** Treść punktu bez punktora, kontynuacje sklejone spacją. */
        public String text() {
            StringBuilder sb = new StringBuilder(CvStructureParser.stripBullet(lines.get(0)).strip());
            for (int i = 1; i < lines.size(); i++) {
                sb.append(' ').append(lines.get(i).strip());
            }
            return sb.toString();
        }

        /** Punktor z pierwszej linii (np. „- ”, „• ”) wraz z wcięciem. */
        public String marker() {
            String first = lines.get(0);
            return first.substring(0, first.length() - CvStructureParser.stripBullet(first).length());
        }
    }

    public Optional<Section> first(CvSectionKind kind) {
        return sections.stream().filter(s -> s.kind() == kind).findFirst();
    }

    /** Wszystkie stanowiska ze wszystkich sekcji doświadczenia. */
    public List<Role> roles() {
        return sections.stream().flatMap(s -> s.roles().stream()).toList();
    }

    /** Czy nagłówki sekcji są pisane wielkimi literami (styl przenoszony na nowe nagłówki). */
    public boolean upperCaseHeadings() {
        List<String> headings = sections.stream().map(Section::heading).filter(h -> h != null).toList();
        if (headings.isEmpty()) {
            return true;
        }
        return headings.stream().allMatch(h -> h.equals(h.toUpperCase()));
    }
}

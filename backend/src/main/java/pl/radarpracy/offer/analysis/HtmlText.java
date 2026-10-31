package pl.radarpracy.offer.analysis;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.Set;

/**
 * Zamiana HTML na czytelny tekst z zachowaniem struktury (akapity, nagłówki, punkty list „- …”),
 * tak aby parser ogłoszeń (#13) rozpoznał sekcje „Wymagania”, „Oferujemy” itd.
 */
final class HtmlText {

    /** Bloki oddzielane pustą linią (akapity). */
    private static final Set<String> PARAGRAPH = Set.of(
            "p", "section", "article", "main", "header", "footer", "table", "blockquote", "pre", "dl", "figure", "hr",
            "details");
    /** Bloki zaczynające się od nowej linii. */
    private static final Set<String> BLOCK = Set.of(
            "div", "tr", "dt", "dd", "form", "fieldset", "address", "summary", "figcaption", "aside", "nav", "tbody",
            "thead");
    private static final Set<String> HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> LISTS = Set.of("ul", "ol");

    /** Elementy, które nigdy nie są treścią ogłoszenia. */
    static final String NOISE = "script, style, noscript, template, svg, iframe, object, embed, canvas, nav, aside, "
            + "button, select, input, textarea, [role=navigation], [role=banner], [role=contentinfo], [role=dialog], "
            + "[aria-hidden=true], [hidden], [class*=cookie], [id*=cookie], [class*=consent], [id*=consent], "
            + "[class*=newsletter], [class*=breadcrumb]";

    private HtmlText() {
    }

    /** Tekst z fragmentu HTML (np. pola {@code description} w JSON-LD). */
    static String fromHtmlFragment(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document d = Jsoup.parseBodyFragment(html.contains("<") ? html : html.replace("\n", "<br>"));
        d.select("script, style, noscript, template, svg, iframe").remove();
        return toText(d.body());
    }

    /**
     * Główna treść strony: usuwa nawigację, stopki, skrypty i banery; wybiera {@code <main>},
     * {@code <article>} lub element z ofertą o najdłuższym tekście, w ostateczności {@code <body>}.
     */
    static String mainContent(Document doc) {
        Document clean = doc.clone();
        clean.select(NOISE).remove();
        // nagłówki/stopki strony (ale nie nagłówek wewnątrz artykułu z tytułem oferty)
        for (Element e : clean.select("header, footer")) {
            if (e.parents().stream().noneMatch(p -> p.nameIs("article") || p.nameIs("main"))) {
                e.remove();
            }
        }
        Element best = null;
        int bestLen = 0;
        Elements candidates = clean.select("[itemtype*=JobPosting], article, main, [role=main], "
                + "[class*=job-description], [class*=jobDescription], [id*=job-description], [class*=offer-description], "
                + "[id*=offer], [class*=offer-content], [class*=posting]");
        for (Element c : candidates) {
            int len = c.text().length();
            if (len > bestLen) {
                best = c;
                bestLen = len;
            }
        }
        Element root = best != null && bestLen >= 200 ? best : clean.body();
        if (root == null) {
            return "";
        }
        String text = toText(root);
        // tytuł strony (h1) może być poza głównym kontenerem — dopisz go na początku
        Element h1 = clean.selectFirst("h1");
        String title = h1 != null ? h1.text().strip() : "";
        if (!title.isEmpty() && !text.startsWith(title) && !text.contains(title)) {
            text = title + "\n\n" + text;
        }
        return text;
    }

    /** Tekst elementu z podziałem na linie (akapity, nagłówki, {@code <br>}, punkty list „- …”). */
    static String toText(Element root) {
        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    appendText(sb, t.text());
                    return;
                }
                if (!(node instanceof Element e)) {
                    return;
                }
                String name = e.normalName();
                if (name.equals("br")) {
                    sb.append('\n');
                } else if (name.equals("li")) {
                    newline(sb);
                    sb.append("- ");
                } else if (name.equals("td") || name.equals("th")) {
                    appendText(sb, " ");
                } else if (HEADINGS.contains(name) || PARAGRAPH.contains(name)) {
                    paragraphBreak(sb);
                } else if (LISTS.contains(name) || BLOCK.contains(name)) {
                    newline(sb);
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (!(node instanceof Element e)) {
                    return;
                }
                String name = e.normalName();
                if (HEADINGS.contains(name) || name.equals("li") || BLOCK.contains(name)) {
                    newline(sb);
                } else if (PARAGRAPH.contains(name) || LISTS.contains(name)) {
                    paragraphBreak(sb);
                }
            }
        }, root);
        return tidy(sb.toString());
    }

    private static void paragraphBreak(StringBuilder sb) {
        if (sb.isEmpty()) {
            return;
        }
        newline(sb);
        if (sb.length() < 2 || sb.charAt(sb.length() - 2) != '\n') {
            sb.append('\n');
        }
    }

    private static void appendText(StringBuilder sb, String text) {
        if (text.isEmpty()) {
            return;
        }
        char last = sb.isEmpty() ? '\n' : sb.charAt(sb.length() - 1);
        String t = text;
        if (last == '\n' || last == ' ') {
            t = t.stripLeading();
        }
        sb.append(t.replace(' ', ' '));
    }

    private static void newline(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    /** Przycina spacje w liniach, usuwa puste punkty i nadmiar pustych linii. */
    static String tidy(String s) {
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : s.split("\n", -1)) {
            String t = line.strip().replaceAll("[ \\t]{2,}", " ");
            if (t.equals("-")) {
                continue;
            }
            if (t.isEmpty()) {
                blank++;
                if (blank > 1) {
                    continue;
                }
            } else {
                blank = 0;
            }
            out.append(t).append('\n');
        }
        return out.toString().strip();
    }
}

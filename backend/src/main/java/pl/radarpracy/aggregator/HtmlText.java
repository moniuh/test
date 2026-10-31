package pl.radarpracy.aggregator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML → czytelny tekst (Jsoup) z zachowaniem akapitów i punktorów, żeby
 * {@link pl.radarpracy.offer.RequirementExtractor} rozpoznał sekcje „Wymagania” i ich punkty.
 *
 * <p>Elementy ukryte stylami (display:none, visibility:hidden, font-size:0, opacity:0, biały tekst
 * bez tła, przesunięcie poza ekran, atrybut {@code hidden}) nie trafiają do tekstu — zwraca je
 * osobno {@link #hiddenTexts(String)}, bo bywają nośnikiem ukrytych poleceń dla AI (pomysł #83).</p>
 */
public final class HtmlText {

    private static final Set<String> SKIP = Set.of("script", "style", "noscript", "template", "head", "svg", "iframe",
            "object", "button", "form", "select", "input", "textarea");
    private static final Set<String> NOT_CONTENT = Set.of("script", "style", "noscript", "template", "head", "svg");
    private static final Set<String> BLOCK = Set.of("p", "div", "section", "article", "header", "footer", "main",
            "aside", "ul", "ol", "li", "h1", "h2", "h3", "h4", "h5", "h6", "table", "tr", "blockquote", "pre",
            "dl", "dt", "dd", "hr", "figure", "figcaption", "address", "nav");
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Pattern FONT_SIZE = Pattern.compile("font-size:([0-9.]+)(px|pt|em|rem|%)?");
    private static final Pattern OPACITY = Pattern.compile("opacity:(0(?:\\.0*[0-9]?)?)(?![0-9])");
    private static final Pattern OFFSCREEN = Pattern.compile("(?:left|top|text-indent|margin-left):-\\d{3,}");
    private static final Pattern WHITE = Pattern.compile(
            "(?<![a-z-])color:(?:#fff(?:fff)?|white|rgba?\\(255,255,255(?:,[0-9.]+)?\\))(?![0-9a-f])");
    private static final Pattern BACKGROUND = Pattern.compile("background(?:-color)?:([^;]+)");

    /** Tekst ukryty w HTML i powód ukrycia. */
    public record HiddenText(String text, String reason) {
    }

    private HtmlText() {
    }

    /** Czy tekst wygląda na HTML (zawiera znaczniki). */
    public static boolean looksLikeHtml(String s) {
        return s != null && HTML_TAG.matcher(s).find();
    }

    /** Widoczny tekst z HTML (albo znormalizowany tekst, gdy wejście nie jest HTML-em). */
    public static String toText(String htmlOrText) {
        if (Text.isBlank(htmlOrText)) {
            return "";
        }
        if (!looksLikeHtml(htmlOrText)) {
            return normalize(htmlOrText);
        }
        Element body = Jsoup.parseBodyFragment(htmlOrText).body();
        StringBuilder sb = new StringBuilder();
        walk(body, sb, false);
        return normalize(sb.toString());
    }

    /** Teksty elementów ukrytych stylami (tylko najwyższe ukryte elementy, bez duplikatów). */
    public static List<HiddenText> hiddenTexts(String html) {
        if (!looksLikeHtml(html)) {
            return List.of();
        }
        return hiddenTexts(Jsoup.parse(html).body());
    }

    public static List<HiddenText> hiddenTexts(Element root) {
        List<HiddenText> out = new ArrayList<>();
        collectHidden(root, out);
        return out;
    }

    private static void collectHidden(Element e, List<HiddenText> out) {
        if (NOT_CONTENT.contains(e.normalName())) {
            return;
        }
        String reason = hiddenReason(e);
        if (reason != null) {
            StringBuilder sb = new StringBuilder();
            walk(e, sb, true);
            String text = normalize(sb.toString());
            if (!text.isBlank()) {
                out.add(new HiddenText(text, reason));
            }
            return;
        }
        for (Element child : e.children()) {
            collectHidden(child, out);
        }
    }

    /** Powód ukrycia elementu (po polsku) albo null, gdy element jest widoczny. */
    static String hiddenReason(Element e) {
        if (e.hasAttr("hidden")) {
            return "atrybut hidden";
        }
        String style = e.attr("style");
        if (style.isBlank()) {
            return null;
        }
        String s = style.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace("!important", "");
        if (s.contains("display:none")) {
            return "display:none";
        }
        if (s.contains("visibility:hidden") || s.contains("visibility:collapse")) {
            return "visibility:hidden";
        }
        Matcher fs = FONT_SIZE.matcher(s);
        while (fs.find()) {
            double size = Double.parseDouble(fs.group(1).endsWith(".") ? fs.group(1) + "0" : fs.group(1));
            String unit = fs.group(2) == null ? "px" : fs.group(2);
            boolean tiny = switch (unit) {
                case "em", "rem" -> size < 0.2;
                case "%" -> size < 20;
                default -> size < 2;
            };
            if (tiny) {
                return "font-size:" + fs.group(1) + (fs.group(2) == null ? "" : fs.group(2));
            }
        }
        if (OPACITY.matcher(s).find()) {
            return "opacity:0";
        }
        if (OFFSCREEN.matcher(s).find()) {
            return "tekst przesunięty poza ekran";
        }
        if ((s.contains("height:0") || s.contains("width:0")) && s.contains("overflow:hidden")) {
            return "zerowy rozmiar z overflow:hidden";
        }
        if (WHITE.matcher(s).find() && !hasColoredBackground(e)) {
            return "biały tekst na białym tle";
        }
        return null;
    }

    /** Czy element lub któryś z kilku przodków ma w stylu inline tło inne niż białe/przezroczyste. */
    private static boolean hasColoredBackground(Element e) {
        Element current = e;
        for (int depth = 0; current != null && depth < 6; depth++, current = current.parent()) {
            String style = current.attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            Matcher bg = BACKGROUND.matcher(style);
            while (bg.find()) {
                String v = bg.group(1);
                if (!(v.startsWith("#fff") || v.startsWith("white") || v.startsWith("rgb(255,255,255")
                        || v.startsWith("transparent") || v.startsWith("none") || v.startsWith("inherit"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void walk(Node node, StringBuilder sb, boolean includeHidden) {
        if (node instanceof TextNode t) {
            String text = t.getWholeText().replaceAll("[\\s\\u00a0]+", " ");
            sb.append(text);
            return;
        }
        if (!(node instanceof Element e)) {
            return;
        }
        String tag = e.normalName();
        if (SKIP.contains(tag) || (!includeHidden && hiddenReason(e) != null)) {
            return;
        }
        if ("br".equals(tag)) {
            sb.append('\n');
            return;
        }
        boolean block = BLOCK.contains(tag);
        if (block) {
            newline(sb);
        }
        if ("li".equals(tag)) {
            sb.append("- ");
        }
        for (Node child : e.childNodes()) {
            walk(child, sb, includeHidden);
        }
        if ("td".equals(tag) || "th".equals(tag)) {
            sb.append(' ');
        }
        if (block) {
            newline(sb);
        }
    }

    private static void newline(StringBuilder sb) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    /** Przycięte linie, pojedyncze spacje, co najwyżej jedna pusta linia między akapitami. */
    static String normalize(String s) {
        String[] lines = s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean blank = false;
        for (String line : lines) {
            String t = line.replaceAll("[ \\t\\u00a0]+", " ").strip();
            if (t.isEmpty()) {
                blank = !out.isEmpty();
                continue;
            }
            if (!out.isEmpty()) {
                out.append(blank ? "\n\n" : "\n");
            }
            out.append(t);
            blank = false;
        }
        return out.toString();
    }
}

package pl.radarpracy.aggregator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import pl.radarpracy.common.Text;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Parser kanałów RSS 2.0 (także RSS 1.0/RDF) i Atom na bazie DOM z JDK (pomysł #9).
 *
 * <p>Bezpieczeństwo: deklaracje DOCTYPE są odrzucane, a encje i DTD zewnętrzne wyłączone (ochrona przed
 * XXE i „billion laughs”). Treść HTML wpisów zamieniana jest na tekst przez Jsoup.</p>
 */
public final class FeedParser {

    public enum Format { RSS, ATOM }

    /** Wynik parsowania: format i pozycje. */
    public record Feed(Format format, String title, List<FeedItem> items) {
    }

    private FeedParser() {
    }

    public static Feed parse(String xml, String baseUrl) {
        return parse(xml == null ? new byte[0] : xml.getBytes(StandardCharsets.UTF_8), baseUrl);
    }

    public static Feed parse(byte[] xml, String baseUrl) {
        Document doc = secureParse(xml);
        Element root = doc.getDocumentElement();
        String rootName = localName(root);
        return switch (rootName) {
            case "rss" -> {
                Element channel = firstChild(root, "channel");
                if (channel == null) {
                    throw new SourceException("Kanał RSS nie zawiera elementu <channel>");
                }
                List<FeedItem> items = new ArrayList<>();
                for (Element item : children(channel, "item")) {
                    items.add(rssItem(item, baseUrl));
                }
                yield new Feed(Format.RSS, childText(channel, "title"), items);
            }
            case "RDF" -> {
                List<FeedItem> items = new ArrayList<>();
                for (Element item : children(root, "item")) {
                    items.add(rssItem(item, baseUrl));
                }
                Element channel = firstChild(root, "channel");
                yield new Feed(Format.RSS, channel == null ? null : childText(channel, "title"), items);
            }
            case "feed" -> {
                List<FeedItem> items = new ArrayList<>();
                for (Element entry : children(root, "entry")) {
                    items.add(atomEntry(entry, baseUrl));
                }
                yield new Feed(Format.ATOM, childText(root, "title"), items);
            }
            default -> throw new SourceException("To nie jest kanał RSS ani Atom (element główny <" + rootName + ">)");
        };
    }

    /** Bezpieczny parser DOM: bez DOCTYPE, bez encji i DTD zewnętrznych. */
    static Document secureParse(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("Zewnętrzne encje są zablokowane");
            });
            b.setErrorHandler(SILENT);
            return b.parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            String msg = Text.nullToEmpty(e.getMessage());
            if (msg.contains("DOCTYPE") || msg.contains("disallow-doctype-decl")) {
                throw new SourceException(
                        "Kanał zawiera deklarację DOCTYPE — odrzucono ze względów bezpieczeństwa (ochrona przed XXE)");
            }
            throw new SourceException("Niepoprawny XML kanału (linia " + e.getLineNumber() + "): " + msg);
        } catch (SAXException e) {
            throw new SourceException("Niepoprawny XML kanału: " + e.getMessage());
        } catch (ParserConfigurationException | IOException e) {
            throw new SourceException("Nie udało się odczytać kanału: " + e.getMessage());
        }
    }

    private static final ErrorHandler SILENT = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    // ------------------------------------------------------------------ RSS

    private static FeedItem rssItem(Element item, String baseUrl) {
        String rawTitle = HtmlText.toText(childText(item, "title"));
        String link = absolute(childText(item, "link"), baseUrl);
        Element guidEl = firstChild(item, "guid");
        String guid = guidEl == null ? null : guidEl.getTextContent().strip();
        if (link == null && guidEl != null && !"false".equalsIgnoreCase(guidEl.getAttribute("isPermaLink"))) {
            link = absolute(guid, baseUrl);
        }
        String description = childText(item, "description");
        String encoded = childText(item, "encoded");
        String html = longer(encoded, description);
        String published = firstNonBlank(childText(item, "pubDate"), childText(item, "date"),
                childText(item, "published"), childText(item, "updated"));
        String author = firstNonBlank(childText(item, "creator"), childText(item, "author"));
        List<String> categories = new ArrayList<>();
        for (Element c : children(item, "category")) {
            String v = c.getTextContent().strip();
            if (!v.isEmpty()) {
                categories.add(v);
            }
        }
        return build(rawTitle, link, firstNonBlank(guid, link), html, Dates.parse(published), author, categories,
                childText(item, "company"), firstNonBlank(childText(item, "location"), childText(item, "city")),
                Dates.parse(firstNonBlank(childText(item, "expires"), childText(item, "validThrough"))));
    }

    // ------------------------------------------------------------------ Atom

    private static FeedItem atomEntry(Element entry, String baseUrl) {
        String rawTitle = textConstruct(firstChild(entry, "title"));
        String link = null;
        for (Element l : children(entry, "link")) {
            String rel = l.getAttribute("rel");
            if (rel.isEmpty() || "alternate".equals(rel)) {
                link = absolute(l.getAttribute("href"), baseUrl);
                break;
            }
        }
        if (link == null) {
            Element any = firstChild(entry, "link");
            link = any == null ? null : absolute(any.getAttribute("href"), baseUrl);
        }
        String id = childText(entry, "id");
        String html = longer(htmlOf(firstChild(entry, "content")), htmlOf(firstChild(entry, "summary")));
        String published = firstNonBlank(childText(entry, "published"), childText(entry, "updated"));
        String author = null;
        Element authorEl = firstChild(entry, "author");
        if (authorEl != null) {
            author = firstNonBlank(childText(authorEl, "name"), authorEl.getTextContent());
        }
        List<String> categories = new ArrayList<>();
        for (Element c : children(entry, "category")) {
            String v = firstNonBlank(c.getAttribute("label"), c.getAttribute("term"));
            if (v != null) {
                categories.add(v);
            }
        }
        return build(HtmlText.toText(rawTitle), link, firstNonBlank(id, link), html, Dates.parse(published), author,
                categories, childText(entry, "company"), childText(entry, "location"), null);
    }

    /** Treść elementu tekstowego Atom jako HTML (type="html" / "xhtml") albo tekst. */
    private static String htmlOf(Element e) {
        if (e == null) {
            return null;
        }
        String type = e.getAttribute("type").toLowerCase(Locale.ROOT);
        if (type.equals("xhtml") || type.endsWith("xhtml+xml")) {
            StringBuilder sb = new StringBuilder();
            NodeList nodes = e.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                serialize(nodes.item(i), sb);
            }
            return sb.toString();
        }
        // type="html" (HTML jako tekst z encjami) albo "text"
        return e.getTextContent();
    }

    private static String textConstruct(Element e) {
        if (e == null) {
            return null;
        }
        String type = e.getAttribute("type").toLowerCase(Locale.ROOT);
        return type.equals("text") || type.isEmpty() ? e.getTextContent() : htmlOf(e);
    }

    /** Minimalna serializacja DOM → HTML (dla treści XHTML w Atom). */
    private static void serialize(Node n, StringBuilder sb) {
        if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
            sb.append(escape(n.getNodeValue()));
        } else if (n.getNodeType() == Node.ELEMENT_NODE) {
            String name = localName((Element) n);
            sb.append('<').append(name);
            NamedNodeMap attrs = n.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node a = attrs.item(i);
                if (!a.getNodeName().startsWith("xmlns")) {
                    sb.append(' ').append(a.getNodeName()).append("=\"").append(escape(a.getNodeValue())).append('"');
                }
            }
            sb.append('>');
            NodeList children = n.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                serialize(children.item(i), sb);
            }
            sb.append("</").append(name).append('>');
        }
    }

    // ------------------------------------------------------------------ wspólne

    private static FeedItem build(String rawTitle, String link, String externalId, String html, LocalDate published,
                                  String author, List<String> categories, String explicitCompany,
                                  String explicitLocation, LocalDate expires) {
        TitleSplitter.Parts parts = TitleSplitter.split(rawTitle);
        String company = firstNonBlank(explicitCompany, parts.company());
        if (company == null && author != null && !author.contains("@") && author.strip().length() <= 80) {
            company = author.strip();
        }
        String location = firstNonBlank(explicitLocation, parts.location());
        String description = HtmlText.toText(html);
        List<HtmlText.HiddenText> hidden = HtmlText.hiddenTexts(html);
        String ext = externalId;
        if (Text.isBlank(ext)) {
            ext = "sha:" + sha256(Text.nullToEmpty(rawTitle) + "\n" + description).substring(0, 32);
        }
        String title = Text.isBlank(parts.title()) ? "(bez tytułu)" : parts.title();
        return new FeedItem(Text.truncate(ext, 1000), Text.truncate(title, 250), Text.truncate(company, 250),
                Text.truncate(location, 250), link, description, published, expires, author, categories, List.of(),
                null, null, hidden);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adres bezwzględny http(s) albo null (np. „javascript:”, „mailto:”). */
    static String absolute(String href, String baseUrl) {
        if (Text.isBlank(href)) {
            return null;
        }
        try {
            URI uri = new URI(href.strip());
            if (!uri.isAbsolute() && baseUrl != null && baseUrl.matches("(?i)https?://.*")) {
                uri = new URI(baseUrl).resolve(uri);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme.equals("http") || scheme.equals("https") ? uri.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String longer(String a, String b) {
        if (Text.isBlank(a)) {
            return b;
        }
        if (Text.isBlank(b)) {
            return a;
        }
        return a.length() >= b.length() ? a : b;
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!Text.isBlank(v)) {
                return v.strip();
            }
        }
        return null;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String localName(Element e) {
        String n = e.getLocalName();
        return n != null ? n : e.getNodeName();
    }

    private static Element firstChild(Element parent, String localName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && localName.equals(localName(e))) {
                return e;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && localName.equals(localName(e))) {
                out.add(e);
            }
        }
        return out;
    }

    private static String childText(Element parent, String localName) {
        Element e = firstChild(parent, localName);
        if (e == null) {
            return null;
        }
        String t = e.getTextContent();
        return t == null || t.isBlank() ? null : t.strip();
    }
}

package pl.radarpracy.aggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Uproszczona obsługa robots.txt (RFC 9309): grupy {@code User-agent}, reguły {@code Allow}/{@code Disallow}
 * z symbolami {@code *} i {@code $}; wygrywa najdłuższa pasująca reguła, przy remisie — Allow.
 * Grupa dla naszego agenta (po nazwie produktu z User-Agent) ma pierwszeństwo przed {@code *}.
 */
public final class RobotsTxt {

    private record Rule(boolean allow, String pattern, Pattern regex) {
    }

    private record Group(List<String> agents, List<Rule> rules) {
    }

    private final List<Group> groups;

    private RobotsTxt(List<Group> groups) {
        this.groups = groups;
    }

    /** robots.txt bez ograniczeń (np. plik nie istnieje — HTTP 404). */
    public static RobotsTxt allowAll() {
        return new RobotsTxt(List.of());
    }

    /** robots.txt blokujący wszystko (np. serwer zwrócił błąd 5xx — postępujemy ostrożnie). */
    public static RobotsTxt disallowAll() {
        return parse("User-agent: *\nDisallow: /");
    }

    public static RobotsTxt parse(String content) {
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasAgent = false;
        if (content != null) {
            for (String rawLine : content.split("\\R")) {
                String line = rawLine;
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).strip();
                switch (key) {
                    case "user-agent" -> {
                        if (current == null || !lastWasAgent) {
                            current = new Group(new ArrayList<>(), new ArrayList<>());
                            groups.add(current);
                        }
                        current.agents().add(value.toLowerCase(Locale.ROOT));
                        lastWasAgent = true;
                    }
                    case "allow", "disallow" -> {
                        lastWasAgent = false;
                        if (current == null) {
                            continue;
                        }
                        if (value.isEmpty()) {
                            continue; // „Disallow:” bez ścieżki = brak ograniczeń
                        }
                        current.rules().add(new Rule(key.equals("allow"), value, toRegex(value)));
                    }
                    default -> lastWasAgent = false;
                }
            }
        }
        return new RobotsTxt(groups);
    }

    /**
     * Czy agent może pobrać ścieżkę.
     *
     * @param userAgent pełny User-Agent (np. „RadarPracy/0.2 (…)”) — liczy się nazwa produktu przed „/”
     * @param pathAndQuery ścieżka z zapytaniem, np. „/kariera?strona=2”
     */
    public boolean isAllowed(String userAgent, String pathAndQuery) {
        String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
        if (path.equals("/robots.txt")) {
            return true;
        }
        List<Rule> rules = rulesFor(userAgent);
        Rule best = null;
        for (Rule r : rules) {
            if (r.regex().matcher(path).lookingAt()) {
                if (best == null || r.pattern().length() > best.pattern().length()
                        || (r.pattern().length() == best.pattern().length() && r.allow() && !best.allow())) {
                    best = r;
                }
            }
        }
        return best == null || best.allow();
    }

    private List<Rule> rulesFor(String userAgent) {
        String product = productToken(userAgent);
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        boolean specificFound = false;
        for (Group g : groups) {
            boolean matches = false;
            boolean star = false;
            for (String a : g.agents()) {
                if (a.equals("*")) {
                    star = true;
                } else if (!product.isEmpty() && (product.contains(a) || a.contains(product))) {
                    matches = true;
                }
            }
            if (matches) {
                specificFound = true;
                specific.addAll(g.rules());
            } else if (star) {
                wildcard.addAll(g.rules());
            }
        }
        return specificFound ? specific : wildcard;
    }

    static String productToken(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        String t = userAgent.strip();
        int cut = t.length();
        for (char c : new char[]{'/', ' ', '('}) {
            int i = t.indexOf(c);
            if (i > 0) {
                cut = Math.min(cut, i);
            }
        }
        return t.substring(0, cut).toLowerCase(Locale.ROOT);
    }

    private static Pattern toRegex(String pattern) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        List<String> parts = new ArrayList<>();
        for (String part : p.split("\\*", -1)) {
            parts.add(Pattern.quote(part));
        }
        // „*” = dowolny ciąg znaków; dopasowanie od początku ścieżki (lookingAt)
        StringBuilder sb = new StringBuilder(String.join(".*", parts));
        if (anchored) {
            sb.append('$');
        }
        return Pattern.compile(sb.toString());
    }
}

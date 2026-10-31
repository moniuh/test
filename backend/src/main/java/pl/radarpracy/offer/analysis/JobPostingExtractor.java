package pl.radarpracy.offer.analysis;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.salary.SalaryPeriod;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Odczyt danych strukturalnych schema.org {@code JobPosting} z bloków
 * {@code <script type="application/ld+json">} — większość portali z ofertami je publikuje
 * (wymóg Google for Jobs). Obsługuje tablice, {@code @graph} i różne zapisy pól.
 */
final class JobPostingExtractor {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JobPostingExtractor() {
    }

    static Optional<JobPostingData> extract(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(json.strip());
            } catch (JacksonException e) {
                continue; // uszkodzony blok — pomijamy
            }
            Optional<JsonNode> posting = findPosting(root, 0);
            if (posting.isPresent()) {
                return Optional.of(toData(posting.get(), json));
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonNode> findPosting(JsonNode node, int depth) {
        if (node == null || depth > 6) {
            return Optional.empty();
        }
        if (node.isArray()) {
            for (JsonNode n : node) {
                Optional<JsonNode> f = findPosting(n, depth + 1);
                if (f.isPresent()) {
                    return f;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        if (isType(node.get("@type"), "JobPosting")) {
            return Optional.of(node);
        }
        return findPosting(node.get("@graph"), depth + 1);
    }

    private static boolean isType(JsonNode type, String expected) {
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (expected.equalsIgnoreCase(str(t))) {
                    return true;
                }
            }
            return false;
        }
        String t = str(type);
        return t != null && (expected.equalsIgnoreCase(t) || t.endsWith("/" + expected));
    }

    private static JobPostingData toData(JsonNode p, String raw) {
        String title = clean(str(p.get("title")));
        if (title == null) {
            title = clean(str(p.get("name")));
        }
        String company = organization(p.get("hiringOrganization"));

        // lokalizacja i praca zdalna
        Set<String> places = new LinkedHashSet<>();
        collectLocations(p.get("jobLocation"), places);
        WorkMode mode = null;
        String locationType = str(p.get("jobLocationType"));
        if (locationType != null && locationType.toUpperCase(Locale.ROOT).contains("TELECOMMUTE")) {
            mode = WorkMode.REMOTE;
        }
        String location = places.isEmpty() ? null : String.join(", ", places.stream().limit(3).toList());

        // wynagrodzenie
        Integer min = null;
        Integer max = null;
        String currency = null;
        SalaryPeriod period = null;
        JsonNode salary = p.get("baseSalary");
        if (salary == null) {
            salary = p.get("estimatedSalary");
        }
        if (salary != null && salary.isArray() && !salary.isEmpty()) {
            salary = salary.get(0);
        }
        if (salary != null && salary.isObject()) {
            currency = clean(str(salary.get("currency")));
            JsonNode value = salary.get("value");
            String unit = str(salary.get("unitText"));
            if (value != null && value.isObject()) {
                min = amount(value.get("minValue"));
                max = amount(value.get("maxValue"));
                if (min == null && max == null) {
                    min = amount(value.get("value"));
                }
                if (unit == null) {
                    unit = str(value.get("unitText"));
                }
                if (currency == null) {
                    currency = clean(str(value.get("currency")));
                }
            } else if (value != null) {
                min = amount(value);
            }
            period = period(unit);
            if (unit != null && unit.equalsIgnoreCase("WEEK")) {
                // tydzień → miesiąc (× 52/12), żeby zmieścić się w modelu ofert
                min = min == null ? null : (int) Math.round(min * 52 / 12.0);
                max = max == null ? null : (int) Math.round(max * 52 / 12.0);
                period = SalaryPeriod.MONTH;
            }
        } else if (salary != null) {
            min = amount(salary);
        }
        if (min != null && max != null && min.equals(max)) {
            max = null;
        }

        // forma zatrudnienia
        List<String> employment = new ArrayList<>();
        JsonNode et = p.get("employmentType");
        if (et != null && et.isArray()) {
            for (JsonNode e : et) {
                if (str(e) != null) {
                    employment.add(str(e));
                }
            }
        } else if (str(et) != null) {
            employment.add(str(et));
        }
        Set<ContractType> contracts = new LinkedHashSet<>();
        for (String e : employment) {
            String u = e.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (u.equals("CONTRACTOR")) {
                contracts.add(ContractType.B2B);
            } else if (u.equals("INTERN") || u.equals("INTERNSHIP")) {
                contracts.add(ContractType.INTERNSHIP);
            } else {
                // polskie portale wpisują tu np. „umowa o pracę”, „B2B”
                EmploymentTermsDetector.contractTypes(e).forEach(d -> contracts.add(d.value()));
            }
        }

        String description = HtmlText.fromHtmlFragment(str(p.get("description")));
        return new JobPostingData(title, company, location, mode, min, max,
                currency == null ? null : currency.toUpperCase(Locale.ROOT), period,
                description.isBlank() ? null : description, date(str(p.get("datePosted"))),
                date(str(p.get("validThrough"))), List.copyOf(contracts), List.copyOf(employment), raw);
    }

    private static String organization(JsonNode org) {
        if (org == null) {
            return null;
        }
        if (org.isArray() && !org.isEmpty()) {
            return organization(org.get(0));
        }
        if (org.isObject()) {
            return clean(str(org.get("name")));
        }
        return clean(str(org));
    }

    private static void collectLocations(JsonNode loc, Set<String> out) {
        if (loc == null) {
            return;
        }
        if (loc.isArray()) {
            for (JsonNode l : loc) {
                collectLocations(l, out);
            }
            return;
        }
        if (!loc.isObject()) {
            String s = clean(str(loc));
            if (s != null) {
                out.add(s);
            }
            return;
        }
        JsonNode address = loc.get("address");
        if (address == null) {
            String name = clean(str(loc.get("name")));
            if (name != null) {
                out.add(name);
            }
            return;
        }
        if (address.isArray() && !address.isEmpty()) {
            address = address.get(0);
        }
        if (!address.isObject()) {
            String s = clean(str(address));
            if (s != null) {
                out.add(s);
            }
            return;
        }
        String locality = clean(str(address.get("addressLocality")));
        if (locality == null) {
            locality = clean(str(address.get("addressRegion")));
        }
        if (locality != null) {
            out.add(locality);
        }
    }

    private static Integer amount(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String s = str(n);
        if (s == null) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(s.replaceAll("[\\s\\u00a0]", "").replace(',', '.'));
            if (v.signum() <= 0 || v.compareTo(BigDecimal.valueOf(10_000_000)) > 0) {
                return null;
            }
            return v.setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static SalaryPeriod period(String unit) {
        if (unit == null) {
            return null;
        }
        return switch (unit.toUpperCase(Locale.ROOT)) {
            case "HOUR" -> SalaryPeriod.HOUR;
            case "DAY" -> SalaryPeriod.DAY;
            case "YEAR" -> SalaryPeriod.YEAR;
            case "MONTH", "WEEK" -> SalaryPeriod.MONTH;
            default -> null;
        };
    }

    private static LocalDate date(String s) {
        if (s == null || s.length() < 10 || !s.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Wartość skalarna jako tekst albo null (obiekty i tablice → null). */
    private static String str(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode() || n.isArray() || n.isObject()) {
            return null;
        }
        String s = n.asString();
        return s == null || s.isBlank() ? null : s;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.contains("<") || s.contains("&") ? HtmlText.fromHtmlFragment(s) : s;
        t = t.replaceAll("\\s+", " ").strip();
        return Text.isBlank(t) ? null : t;
    }
}

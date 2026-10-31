package pl.radarpracy.offer.analysis;

import pl.radarpracy.common.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lista większych polskich miast z odmianą (mianownik, miejscownik, dopełniacz) — do rozpoznawania
 * lokalizacji w ogłoszeniach („w Krakowie”, „Wrocław / zdalnie”).
 */
final class PolishCities {

    /** Wystąpienie miasta w tekście (indeksy w oryginale). */
    record CityHit(String city, int start, int end) {
    }

    private static final Map<String, List<String>> CITIES = new LinkedHashMap<>();

    static {
        add("Warszawa", "warszawa", "warszawie", "warszawy", "warszawe");
        add("Kraków", "krakow", "krakowie", "krakowa");
        add("Wrocław", "wroclaw", "wroclawiu", "wroclawia");
        add("Poznań", "poznan", "poznaniu", "poznania");
        add("Gdańsk", "gdansk", "gdansku", "gdanska");
        add("Gdynia", "gdynia", "gdyni");
        add("Sopot", "sopot", "sopocie");
        add("Trójmiasto", "trojmiasto", "trojmiescie", "trojmiasta");
        add("Łódź", "lodz", "lodzi");
        add("Katowice", "katowice", "katowicach");
        add("Szczecin", "szczecin", "szczecinie", "szczecina");
        add("Lublin", "lublin", "lublinie", "lublina");
        add("Bydgoszcz", "bydgoszcz", "bydgoszczy");
        add("Białystok", "bialystok", "bialymstoku", "bialegostoku");
        add("Toruń", "torun", "toruniu", "torunia");
        add("Rzeszów", "rzeszow", "rzeszowie", "rzeszowa");
        add("Kielce", "kielce", "kielcach");
        add("Olsztyn", "olsztyn", "olsztynie", "olsztyna");
        add("Opole", "opole", "opolu");
        add("Gliwice", "gliwice", "gliwicach");
        add("Zabrze", "zabrze", "zabrzu");
        add("Bielsko-Biała", "bielsko-biala", "bielsku-bialej", "bielsko biala");
        add("Częstochowa", "czestochowa", "czestochowie");
        add("Radom", "radom", "radomiu");
        add("Sosnowiec", "sosnowiec", "sosnowcu");
        add("Zielona Góra", "zielona gora", "zielonej gorze");
        add("Gorzów Wielkopolski", "gorzow wielkopolski", "gorzowie wielkopolskim", "gorzow wlkp");
        add("Płock", "plock", "plocku");
        add("Elbląg", "elblag", "elblagu");
        add("Tarnów", "tarnow", "tarnowie");
        add("Legnica", "legnica", "legnicy");
        add("Kalisz", "kalisz", "kaliszu");
        add("Koszalin", "koszalin", "koszalinie");
        add("Słupsk", "slupsk", "slupsku");
        add("Nowy Sącz", "nowy sacz", "nowym saczu");
        add("Tychy", "tychy", "tychach");
        add("Rybnik", "rybnik", "rybniku");
        add("Chorzów", "chorzow", "chorzowie");
        add("Bytom", "bytom", "bytomiu");
        add("Piaseczno", "piaseczno", "piasecznie");
        add("Pruszków", "pruszkow", "pruszkowie");
    }

    private static final List<Map.Entry<String, Pattern>> PATTERNS = new ArrayList<>();

    static {
        List<Map.Entry<String, String>> forms = new ArrayList<>();
        CITIES.forEach((city, fs) -> fs.forEach(f -> forms.add(Map.entry(city, f))));
        forms.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getValue().length()).reversed());
        for (var e : forms) {
            PATTERNS.add(Map.entry(e.getKey(),
                    Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(e.getValue()).replace(" ", "\\E\\s+\\Q") + "(?![\\p{L}\\d])")));
        }
    }

    private PolishCities() {
    }

    private static void add(String city, String... forms) {
        CITIES.put(city, List.of(forms));
    }

    /**
     * Miasta wymienione w tekście, w kolejności wystąpienia. Wymagamy wielkiej litery w oryginale,
     * żeby „do poznania” czy „łodzi” (łódź-pojazd) nie były brane za miasto.
     */
    static List<CityHit> find(String text) {
        List<CityHit> hits = new ArrayList<>();
        if (Text.isBlank(text)) {
            return hits;
        }
        String folded = Text.fold(text);
        boolean[] taken = new boolean[folded.length()];
        for (var e : PATTERNS) {
            Matcher m = e.getValue().matcher(folded);
            while (m.find()) {
                if (!Character.isUpperCase(text.charAt(m.start())) || overlaps(taken, m.start(), m.end())) {
                    continue;
                }
                for (int i = m.start(); i < m.end(); i++) {
                    taken[i] = true;
                }
                hits.add(new CityHit(e.getKey(), m.start(), m.end()));
            }
        }
        hits.sort(Comparator.comparingInt(CityHit::start));
        return hits;
    }

    private static boolean overlaps(boolean[] taken, int from, int to) {
        for (int i = from; i < to; i++) {
            if (taken[i]) {
                return true;
            }
        }
        return false;
    }

    /** Czy tekst jest nazwą miasta z listy (dowolna odmiana). */
    static boolean isCity(String s) {
        if (Text.isBlank(s)) {
            return false;
        }
        String f = Text.fold(s).strip();
        return CITIES.values().stream().anyMatch(forms -> forms.contains(f));
    }
}

package pl.radarpracy.aggregator;

import pl.radarpracy.salary.SalaryRange;

import java.time.LocalDate;
import java.util.List;

/**
 * Pozycja odczytana ze źródła (RSS/Atom/JSON-LD), przed analizą i zapisem.
 *
 * @param externalId      identyfikator w źródle (guid / id / identifier / url)
 * @param title           stanowisko (po rozbiciu tytułu „Stanowisko — Firma — Miasto”)
 * @param company         firma, jeśli znana
 * @param location        lokalizacja, jeśli znana
 * @param url             adres ogłoszenia (link do oryginału — atrybucja)
 * @param description     widoczny tekst ogłoszenia (HTML zamieniony na tekst)
 * @param publishedAt     data publikacji
 * @param expiresAt       data ważności (validThrough), jeśli podana
 * @param author          autor wpisu (RSS/Atom)
 * @param categories      kategorie wpisu
 * @param employmentTypes typy zatrudnienia z danych strukturalnych (np. FULL_TIME, CONTRACTOR)
 * @param salary          widełki z danych strukturalnych (baseSalary) albo null
 * @param remote          true, gdy dane strukturalne wskazują pracę zdalną (TELECOMMUTE)
 * @param hiddenTexts     teksty ukryte stylami w HTML ogłoszenia lub strony
 */
public record FeedItem(String externalId, String title, String company, String location, String url,
                       String description, LocalDate publishedAt, LocalDate expiresAt, String author,
                       List<String> categories, List<String> employmentTypes, SalaryRange salary, Boolean remote,
                       List<HtmlText.HiddenText> hiddenTexts) {

    public FeedItem {
        categories = categories == null ? List.of() : List.copyOf(categories);
        employmentTypes = employmentTypes == null ? List.of() : List.copyOf(employmentTypes);
        hiddenTexts = hiddenTexts == null ? List.of() : List.copyOf(hiddenTexts);
    }

    public FeedItem withHiddenTexts(List<HtmlText.HiddenText> extra) {
        List<HtmlText.HiddenText> all = new java.util.ArrayList<>(hiddenTexts);
        for (HtmlText.HiddenText h : extra) {
            if (!all.contains(h)) {
                all.add(h);
            }
        }
        return new FeedItem(externalId, title, company, location, url, description, publishedAt, expiresAt, author,
                categories, employmentTypes, salary, remote, all);
    }
}

package pl.radarpracy.profile.cv;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.util.ArrayList;
import java.util.List;

/** Import CV z pliku PDF/DOCX jako nowej wersji CV (pomysł #1). */
@Service
public class CvImportService {

    /** Poniżej tylu słów ostrzegamy, że import mógł się nie udać w całości. */
    static final int SHORT_TEXT_WORDS = 80;
    static final int MAX_NAME_LENGTH = 80;

    private final CvTextExtractor extractor;
    private final ProfileService profiles;

    public CvImportService(CvTextExtractor extractor, ProfileService profiles) {
        this.extractor = extractor;
        this.profiles = profiles;
    }

    /**
     * Wynik importu.
     *
     * @param cv         utworzona wersja CV
     * @param format     „PDF” albo „DOCX”
     * @param pages      liczba stron PDF (null dla DOCX)
     * @param characters liczba znaków tekstu
     * @param words      liczba słów
     * @param warnings   ostrzeżenia do pokazania kandydatowi
     */
    public record CvImportResult(CvVariant cv, String format, Integer pages, int characters, int words,
                                 List<String> warnings) {
    }

    public CvImportResult importFile(String filename, byte[] bytes, String name, String targetRole) {
        CvTextExtractor.Extracted extracted = extractor.extract(filename, bytes);
        CvVariant draft = new CvVariant();
        draft.setName(Text.isBlank(name) ? nameFromFile(filename) : Text.truncate(name.strip(), MAX_NAME_LENGTH));
        draft.setTargetRole(Text.isBlank(targetRole) ? null : targetRole.strip());
        draft.setContent(extracted.text());
        draft.setOrigin("import " + extracted.format().name());
        CvVariant saved = profiles.createCv(draft);

        int words = CvTextNormalizer.wordCount(extracted.text());
        List<String> warnings = new ArrayList<>();
        if (words < SHORT_TEXT_WORDS) {
            warnings.add("Odczytano tylko " + words + " " + Plural.of(words, "słowo", "słowa", "słów")
                    + " — sprawdź, czy cała treść CV została zaimportowana (część tekstu mogła być w obrazach "
                    + "lub polach tekstowych).");
        }
        if (extracted.images() > 0) {
            warnings.add("Plik zawiera " + extracted.images() + " "
                    + Plural.of(extracted.images(), "obraz", "obrazy", "obrazów")
                    + ". Jeśli to Twoje zdjęcie, rozważ jego usunięcie — pracodawca go nie potrzebuje, "
                    + "a systemy ATS i tak go nie odczytają.");
        }
        return new CvImportResult(saved, extracted.format().name(), extracted.pages(), extracted.text().length(),
                words, warnings);
    }

    /** Nazwa wersji z nazwy pliku: „CV_Anna_Nowak_2026.pdf” → „CV Anna Nowak 2026”. */
    static String nameFromFile(String filename) {
        String base = filename == null ? "" : filename.strip();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[_]+", " ").replaceAll("\\s+", " ").strip();
        return base.isEmpty() ? "Zaimportowane CV" : Text.truncate(base, MAX_NAME_LENGTH);
    }
}

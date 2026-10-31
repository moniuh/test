package pl.radarpracy.profile.cv;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import pl.radarpracy.common.BadRequestException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Ekstrakcja tekstu z pliku CV (pomysł #1): PDF przez PDFBox 3, DOCX przez Apache POI.
 *
 * <p>Walidacja: rozszerzenie i sygnatura pliku muszą się zgadzać (PDF ma nagłówek {@code %PDF-},
 * DOCX to archiwum ZIP {@code PK\u0003\u0004}), limit rozmiaru {@value #MAX_BYTES} B, limit stron
 * {@value #MAX_PDF_PAGES}. Pusty tekst (np. skan bez warstwy tekstowej) kończy się czytelnym błędem.</p>
 */
@Component
public class CvTextExtractor {

    /** Limit rozmiaru pliku CV: 5 MB (CV z tekstem ma zwykle kilkaset KB). */
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** CV dłuższe niż 20 stron to najpewniej inny dokument. */
    public static final int MAX_PDF_PAGES = 20;
    /** Minimalna liczba liter, poniżej której uznajemy, że plik nie ma warstwy tekstowej. */
    static final int MIN_LETTERS = 20;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] OLE_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    /** Obsługiwany format pliku. */
    public enum Format {
        PDF, DOCX
    }

    /**
     * Wynik ekstrakcji.
     *
     * @param format format pliku
     * @param text   znormalizowany tekst
     * @param pages  liczba stron (dla DOCX — null, Word nie zapisuje stron w pliku)
     * @param images liczba obrazów w pliku (może to być zdjęcie kandydata)
     */
    public record Extracted(Format format, String text, Integer pages, int images) {
    }

    public Extracted extract(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Wybierz plik CV — przesłany plik jest pusty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("Plik jest za duży (" + megabytes(bytes.length) + " MB). Limit to "
                    + (MAX_BYTES / 1024 / 1024) + " MB — zapisz CV bez osadzonych dużych obrazów.");
        }
        Format format = formatOf(filename);
        checkSignature(format, bytes);
        Extracted raw = format == Format.PDF ? readPdf(bytes) : readDocx(bytes);
        String text = CvTextNormalizer.normalize(raw.text());
        if (letters(text) < MIN_LETTERS) {
            throw new BadRequestException(format == Format.PDF
                    ? "Nie udało się odczytać tekstu z PDF — plik wygląda na skan lub obraz bez warstwy tekstowej "
                    + "(brak OCR). Wyeksportuj CV z edytora jako PDF z tekstem albo wklej treść ręcznie."
                    : "Dokument DOCX nie zawiera tekstu do odczytania (np. treść jest w obrazach lub polach "
                    + "tekstowych). Zapisz CV jako zwykły tekst w Wordzie albo wklej treść ręcznie.");
        }
        return new Extracted(format, text, raw.pages(), raw.images());
    }

    /** Format na podstawie rozszerzenia (walidacja nazwy pliku). */
    static Format formatOf(String filename) {
        String name = filename == null ? "" : filename.strip().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return Format.PDF;
        }
        if (name.endsWith(".docx")) {
            return Format.DOCX;
        }
        if (name.endsWith(".doc")) {
            throw new BadRequestException("Format .doc (Word 97–2003) nie jest obsługiwany — zapisz CV jako DOCX "
                    + "lub PDF i spróbuj ponownie.");
        }
        throw new BadRequestException("Nieobsługiwany typ pliku. Wgraj CV w formacie PDF lub DOCX.");
    }

    private static void checkSignature(Format format, byte[] bytes) {
        // specyfikacja PDF dopuszcza nagłówek %PDF- w pierwszych 1024 bajtach
        if (format == Format.PDF && indexOf(bytes, PDF_MAGIC, 1024) < 0) {
            throw new BadRequestException("Plik ma rozszerzenie .pdf, ale jego zawartość nie jest dokumentem PDF.");
        }
        if (format == Format.DOCX && !startsWith(bytes, ZIP_MAGIC)) {
            throw new BadRequestException(startsWith(bytes, OLE_MAGIC)
                    ? "Plik .docx jest zaszyfrowany hasłem albo zapisany w starym formacie Worda — zapisz go "
                    + "ponownie jako DOCX bez hasła lub jako PDF."
                    : "Plik ma rozszerzenie .docx, ale jego zawartość nie jest dokumentem Word (DOCX).");
        }
    }

    private static Extracted readPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PDF_PAGES) {
                throw new BadRequestException("PDF ma " + pages + " stron — to nie wygląda na CV (limit "
                        + MAX_PDF_PAGES + " stron).");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n");
            String text = stripper.getText(doc);
            int images = 0;
            for (PDPage page : doc.getPages()) {
                images += countImages(page.getResources());
            }
            return new Extracted(Format.PDF, text, pages, images);
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("PDF jest zabezpieczony hasłem — zapisz go bez hasła i spróbuj ponownie.");
        } catch (IOException e) {
            throw new BadRequestException("Nie udało się odczytać pliku PDF — plik może być uszkodzony.");
        }
    }

    private static int countImages(PDResources resources) {
        if (resources == null) {
            return 0;
        }
        int count = 0;
        for (COSName name : resources.getXObjectNames()) {
            try {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    count++;
                }
            } catch (IOException e) {
                // uszkodzony obiekt graficzny nie przeszkadza w odczycie tekstu
            }
        }
        return count;
    }

    private static Extracted readDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return new Extracted(Format.DOCX, extractor.getText(), null, doc.getAllPictures().size());
        } catch (IOException | RuntimeException e) {
            // POI zgłasza m.in. NotOfficeXmlFileException, POIXMLException dla uszkodzonych archiwów
            throw new BadRequestException("Nie udało się odczytać pliku DOCX — plik może być uszkodzony "
                    + "albo nie jest dokumentem Word.");
        }
    }

    private static int indexOf(byte[] bytes, byte[] magic, int within) {
        int last = Math.min(bytes.length - magic.length, within);
        for (int i = 0; i <= last; i++) {
            boolean match = true;
            for (int j = 0; j < magic.length && match; j++) {
                match = bytes[i + j] == magic[j];
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static int letters(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private static String megabytes(int bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0);
    }
}

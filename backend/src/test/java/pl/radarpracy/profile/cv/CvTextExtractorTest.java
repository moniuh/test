package pl.radarpracy.profile.cv;

import org.junit.jupiter.api.Test;
import pl.radarpracy.common.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvTextExtractorTest {

    private final CvTextExtractor extractor = new CvTextExtractor();

    private static final List<String> CV_LINES = List.of(
            "Jan Testowy", "E-mail: jan.testowy@example.com", "DOSWIADCZENIE",
            "- Built REST API in Java and Spring Boot (2021 - 2024)", "UMIEJETNOSCI", "Java, Spring Boot, Docker");

    @Test
    void extractsTextFromPdf() {
        CvTextExtractor.Extracted r = extractor.extract("cv.pdf", CvTestFiles.pdf(CV_LINES));
        assertThat(r.format()).isEqualTo(CvTextExtractor.Format.PDF);
        assertThat(r.pages()).isEqualTo(1);
        assertThat(r.images()).isZero();
        assertThat(r.text()).contains("jan.testowy@example.com").contains("Built REST API in Java and Spring Boot")
                .contains("Java, Spring Boot, Docker");
        assertThat(r.text().lines()).contains("DOSWIADCZENIE");
    }

    @Test
    void extractsTextFromDocxWithPolishCharacters() {
        CvTextExtractor.Extracted r = extractor.extract("CV.DOCX", CvTestFiles.docx(List.of(
                "Anna Kowalska", "Doświadczenie zawodowe", "- Wdrożyłam   mikroserwisy\tw Spring Boot",
                "", "", "", "Umiejętności: Java, PostgreSQL")));
        assertThat(r.format()).isEqualTo(CvTextExtractor.Format.DOCX);
        assertThat(r.pages()).isNull();
        assertThat(r.text()).contains("Wdrożyłam mikroserwisy w Spring Boot").contains("Umiejętności: Java, PostgreSQL");
        assertThat(r.text()).doesNotContain("\n\n\n");
    }

    @Test
    void countsImagesInPdf() {
        CvTextExtractor.Extracted r = extractor.extract("cv.pdf", CvTestFiles.pdf(CV_LINES, true, false));
        assertThat(r.images()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedExtensionsAndOldWordFormat() {
        byte[] pdf = CvTestFiles.pdf(CV_LINES);
        assertThatThrownBy(() -> extractor.extract("cv.txt", pdf)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PDF lub DOCX");
        assertThatThrownBy(() -> extractor.extract("cv.doc", pdf)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining(".doc");
        assertThatThrownBy(() -> extractor.extract(null, pdf)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsSignatureMismatch() {
        byte[] docx = CvTestFiles.docx(List.of("Tekst"));
        byte[] pdf = CvTestFiles.pdf(CV_LINES);
        assertThatThrownBy(() -> extractor.extract("cv.pdf", docx)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nie jest dokumentem PDF");
        assertThatThrownBy(() -> extractor.extract("cv.docx", pdf)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nie jest dokumentem Word");
        byte[] text = "Zwykły tekst udający PDF".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> extractor.extract("cv.pdf", text)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmptyTooLargeAndCorruptedFiles() {
        assertThatThrownBy(() -> extractor.extract("cv.pdf", new byte[0])).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pusty");
        byte[] huge = new byte[CvTextExtractor.MAX_BYTES + 1];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, huge, 0, 8);
        assertThatThrownBy(() -> extractor.extract("cv.pdf", huge)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("za duży");
        byte[] broken = Arrays.copyOf("%PDF-1.7\nto nie jest poprawny PDF".getBytes(StandardCharsets.US_ASCII), 64);
        assertThatThrownBy(() -> extractor.extract("cv.pdf", broken)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PDF");
        byte[] brokenZip = {'P', 'K', 3, 4, 1, 2, 3, 4, 5, 6};
        assertThatThrownBy(() -> extractor.extract("cv.docx", brokenZip)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("DOCX");
    }

    @Test
    void scannedPdfWithoutTextGivesReadableError() {
        byte[] scan = CvTestFiles.pdf(List.of(), true, false);
        assertThatThrownBy(() -> extractor.extract("skan.pdf", scan)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("skan").hasMessageContaining("OCR");
    }

    @Test
    void passwordProtectedPdfIsRejected() {
        byte[] secret = CvTestFiles.pdf(CV_LINES, false, true);
        assertThatThrownBy(() -> extractor.extract("cv.pdf", secret)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hasłem");
    }

    @Test
    void normalizerCleansWhitespaceAndInvisibleCharacters() {
        String raw = "  Anna\u00A0Nowak \r\n\r\n\r\n\r\nE-mail:\tanna@example.com\u00AD\n\uF0B7 Java\u200B  i  SQL  \n";
        String n = CvTextNormalizer.normalize(raw);
        assertThat(n).isEqualTo("Anna Nowak\n\nE-mail: anna@example.com\n• Java i SQL");
        assertThat(CvTextNormalizer.wordCount(n)).isEqualTo(9);
        assertThat(CvTextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void importNameIsDerivedFromFileName() {
        assertThat(CvImportService.nameFromFile("C:\\Users\\anna\\CV_Anna_Nowak_2026.pdf"))
                .isEqualTo("CV Anna Nowak 2026");
        assertThat(CvImportService.nameFromFile("   ")).isEqualTo("Zaimportowane CV");
    }
}

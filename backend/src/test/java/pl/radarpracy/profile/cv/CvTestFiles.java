package pl.radarpracy.profile.cv;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Małe pliki PDF i DOCX generowane w pamięci na potrzeby testów importu. */
final class CvTestFiles {

    private CvTestFiles() {
    }

    /** PDF z liniami tekstu (Helvetica — tylko znaki ASCII/Latin-1). */
    static byte[] pdf(List<String> lines) {
        return pdf(lines, false, false);
    }

    static byte[] pdf(List<String> lines, boolean withImage, boolean encrypted) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                if (!lines.isEmpty()) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.setLeading(14);
                    cs.newLineAtOffset(50, 740);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
                if (withImage) {
                    BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                    PDImageXObject x = LosslessFactory.createFromImage(doc, img);
                    cs.drawImage(x, 400, 600, 80, 80);
                }
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("wlasciciel", "haslo",
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** DOCX z akapitami (pełne polskie znaki). */
    static byte[] docx(List<String> paragraphs) {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String p : paragraphs) {
                doc.createParagraph().createRun().setText(p);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

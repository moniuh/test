package pl.radarpracy.tailoring;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Eksport CV (czysty tekst) do DOCX: imię i nazwisko jako tytuł, nagłówki sekcji pogrubione,
 * linie stanowisk pogrubione, punkty jako lista punktowana Worda (czytelna dla ATS).
 */
@Component
public class DocxExporter {

    private static final String FONT = "Calibri";

    public byte[] export(String content) {
        ParsedCv cv = CvStructureParser.parse(content);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BigInteger bullets = bulletNumbering(doc);
            for (ParsedCv.Section s : cv.sections()) {
                if (s.kind() == CvSectionKind.HEADER) {
                    header(doc, s.nonBlankLines());
                    continue;
                }
                XWPFParagraph h = doc.createParagraph();
                h.setSpacingBefore(240);
                h.setSpacingAfter(80);
                run(h, s.heading().replaceAll("^#+\\s*", "").replaceAll(":$", ""), true, 13);
                if (s.kind() == CvSectionKind.EXPERIENCE) {
                    for (ParsedCv.Role role : s.roles()) {
                        if (role.header() != null) {
                            XWPFParagraph p = doc.createParagraph();
                            p.setSpacingBefore(120);
                            run(p, role.header().strip(), true, 11);
                        }
                        for (String d : role.details()) {
                            run(doc.createParagraph(), d.strip(), false, 11);
                        }
                        for (ParsedCv.Bullet b : role.bullets()) {
                            bullet(doc, bullets, b.text());
                        }
                    }
                } else {
                    for (String line : s.nonBlankLines()) {
                        if (CvStructureParser.isBullet(line)) {
                            bullet(doc, bullets, CvStructureParser.stripBullet(line).strip());
                        } else {
                            run(doc.createParagraph(), line.strip(), false, 11);
                        }
                    }
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udało się utworzyć pliku DOCX", e);
        }
    }

    private static void header(XWPFDocument doc, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            XWPFParagraph p = doc.createParagraph();
            if (i == 0) {
                p.setAlignment(ParagraphAlignment.LEFT);
                run(p, lines.get(i).strip(), true, 18);
            } else {
                run(p, lines.get(i).strip(), i == 1, i == 1 ? 12 : 10);
            }
        }
    }

    private static void bullet(XWPFDocument doc, BigInteger numId, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setNumID(numId);
        run(p, text, false, 11);
    }

    private static void run(XWPFParagraph p, String text, boolean bold, int size) {
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontSize(size);
        r.setFontFamily(FONT);
    }

    /** Definicja listy punktowanej „•” z wcięciem. */
    private static BigInteger bulletNumbering(XWPFDocument doc) {
        CTAbstractNum abs = CTAbstractNum.Factory.newInstance();
        abs.setAbstractNumId(BigInteger.ZERO);
        CTLvl lvl = abs.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
        lvl.addNewLvlText().setVal("•");
        lvl.addNewStart().setVal(BigInteger.ONE);
        CTInd ind = lvl.addNewPPr().addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));
        XWPFNumbering numbering = doc.createNumbering();
        BigInteger absId = numbering.addAbstractNum(new XWPFAbstractNum(abs));
        return numbering.addNum(absId);
    }
}

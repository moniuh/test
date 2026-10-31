package pl.radarpracy.tailoring;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocxExporterTest {

    @Test
    void exportsHeadingsBoldAndBulletsAsList() throws Exception {
        byte[] bytes = new DocxExporter().export(Demo.cvBackend());
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 2)).isEqualTo("PK");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<XWPFParagraph> ps = doc.getParagraphs();
            assertThat(ps.get(0).getText()).isEqualTo("Anna Nowak");
            assertThat(ps.get(0).getRuns().get(0).isBold()).isTrue();
            XWPFParagraph heading = ps.stream().filter(p -> p.getText().equals("DOŚWIADCZENIE")).findFirst()
                    .orElseThrow();
            assertThat(heading.getRuns().get(0).isBold()).isTrue();
            XWPFParagraph role = ps.stream().filter(p -> p.getText().contains("PayFlow")).findFirst().orElseThrow();
            assertThat(role.getRuns().get(0).isBold()).isTrue();
            List<XWPFParagraph> bullets = ps.stream().filter(p -> p.getNumID() != null).toList();
            assertThat(bullets).hasSize(7);
            assertThat(bullets.get(0).getText()).startsWith("Rozwój mikroserwisów").doesNotStartWith("-");
            assertThat(doc.getNumbering()).isNotNull();
            assertThat(ps).anyMatch(p -> p.getText().startsWith("Wyrażam zgodę"));
        }
    }
}

package ruanpao.ishyallm.ingestion.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PdfParserServiceTest {

    @Test
    void shouldExtractTextFromPdf() throws IOException {
        byte[] pdfBytes = createTestPdf("Hello World\nSecond line");

        PdfParserService parser = new PdfParserService();
        String extracted = parser.extractText(pdfBytes);

        assertThat(extracted).contains("Hello World");
        assertThat(extracted).contains("Second line");
    }

    @Test
    void shouldExtractTextFromMultiPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= 3; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.setLeading(14.5f);
                    cs.newLineAtOffset(25, 700);
                    cs.showText("Page " + i + " content");
                    cs.newLine();
                    cs.endText();
                }
            }
            byte[] pdfBytes = toByteArray(doc);

            PdfParserService parser = new PdfParserService();
            String text = parser.extractText(pdfBytes);

            assertThat(text).contains("Page 1 content");
            assertThat(text).contains("Page 2 content");
            assertThat(text).contains("Page 3 content");
        }
    }

    @Test
    void shouldThrowExceptionForInvalidPdf() {
        PdfParserService parser = new PdfParserService();
        byte[] invalidBytes = "not a pdf content".getBytes();

        org.junit.jupiter.api.Assertions.assertThrows(
                IOException.class,
                () -> parser.extractText(invalidBytes)
        );
    }

    // --- helpers ---

    private byte[] createTestPdf(String content) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(25, 700);
                for (String line : content.split("\n")) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            return toByteArray(doc);
        }
    }

    private byte[] toByteArray(PDDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}

package br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf;

import br.com.jeffdesouza.eventknowledge.event.application.ExtractedPage;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxDocumentTextExtractorTest {

    private final PdfBoxDocumentTextExtractor extractor = new PdfBoxDocumentTextExtractor();

    @Test
    void extractsCanonicalPdfsPageByPageWithDocumentAndPageMetadata() throws IOException {
        EventDocument document = new EventDocument("event-guide.pdf");

        try (InputStream content = getClass().getResourceAsStream("/documents/pdfs/event-guide.pdf")) {
            List<ExtractedPage> pages = extractor.extract(document, content);

            assertThat(pages).isNotEmpty();
            assertThat(pages).allSatisfy(page -> {
                assertThat(page.documentName()).isEqualTo("event-guide.pdf");
                assertThat(page.page()).isGreaterThanOrEqualTo(1);
                assertThat(page.text()).isNotBlank();
            });
            assertThat(pages).extracting(ExtractedPage::text)
                    .anyMatch(text -> text.contains("07:30"));
        }
    }

    @Test
    void normalizesOnlyRedundantWhitespaceAndKeepsUsefulPagesRealNumbers() throws IOException {
        EventDocument document = new EventDocument("fixture.pdf");

        List<ExtractedPage> pages = extractor.extract(document, new ByteArrayInputStream(pdfWithPages(
                "   ",
                "  Credenciamento às   07:30.  ",
                "Portão B"
        )));

        assertThat(pages).extracting(ExtractedPage::page).containsExactly(2, 3);
        assertThat(pages.get(0)).isEqualTo(new ExtractedPage("fixture.pdf", 2,
                "Credenciamento às 07:30."));
        assertThat(pages.get(1).text()).isEqualTo("Portão B");
    }

    @Test
    void normalizesInlineWhitespaceAndLineEndingsWithoutRemovingUsefulLineStructure() {
        String text = "  Primeiro\u00A0  item  \r\n\tSegundo\titem\r\n\r\n\r\nTerceiro  \r Quarto  ";

        assertThat(PdfBoxDocumentTextExtractor.normalize(text))
                .isEqualTo("Primeiro item\nSegundo item\n\nTerceiro\nQuarto");
    }

    @Test
    void failsClearlyWhenPdfCannotBeRead() {
        assertThatThrownBy(() -> extractor.extract(new EventDocument("broken.pdf"),
                new ByteArrayInputStream("not a PDF".getBytes())))
                .isInstanceOf(IOException.class)
                .hasMessage("Unable to read PDF document: broken.pdf")
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    void failsClearlyWhenDocumentHasNoUsefulText() throws IOException {
        assertThatThrownBy(() -> extractor.extract(new EventDocument("empty.pdf"),
                new ByteArrayInputStream(pdfWithPages("   ", "\n\t"))))
                .isInstanceOf(IOException.class)
                .hasMessage("PDF document contains no useful text: empty.pdf");
    }

    private static byte[] pdfWithPages(String... texts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : texts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!text.isBlank()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(50, 700);
                        stream.showText(text.replace('\n', ' '));
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}

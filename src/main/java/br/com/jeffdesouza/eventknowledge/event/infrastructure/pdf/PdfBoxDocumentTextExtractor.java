package br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf;

import br.com.jeffdesouza.eventknowledge.event.application.ExtractedPage;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentTextExtractor;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Adapter PDFBox que extrai texto útil por página a partir de um stream. */
public final class PdfBoxDocumentTextExtractor implements DocumentTextExtractor {

    @Override
    public List<ExtractedPage> extract(EventDocument document, InputStream content) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("PDF content must not be null");
        }

        try (RandomAccessReadBuffer input = new RandomAccessReadBuffer(content);
             PDDocument pdf = Loader.loadPDF(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedPage> pages = new ArrayList<>();

            for (int pageNumber = 1; pageNumber <= pdf.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String normalizedText = normalize(stripper.getText(pdf));
                if (!normalizedText.isBlank()) {
                    pages.add(new ExtractedPage(document.name(), pageNumber, normalizedText));
                }
            }

            if (pages.isEmpty()) {
                throw new IOException("PDF document contains no useful text: " + document.name());
            }
            return List.copyOf(pages);
        } catch (IOException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith("PDF document contains no useful text:")) {
                throw exception;
            }
            throw new IOException("Unable to read PDF document: " + document.name(), exception);
        }
    }

    static String normalize(String text) {
        String normalizedLineEndings = text
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] lines = normalizedLineEndings.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            lines[index] = lines[index]
                    .replaceAll("[ \\t]+", " ")
                    .trim();
        }

        return String.join("\n", lines)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}

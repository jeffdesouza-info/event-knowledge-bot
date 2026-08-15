package br.com.jeffdesouza.eventknowledge.event.application;

import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentTextExtractor;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIngestionTest {

    @Test
    void ingestsTheFourCanonicalPdfsIntoAnImmutableResult() throws IOException {
        var result = new KnowledgeIngestion(
                new br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter(),
                new br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter(),
                new br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf.PdfBoxDocumentTextExtractor(),
                new KnowledgeChunker()).ingest();

        assertThat(result.documents()).extracting(EventDocument::name)
                .containsExactly("event-guide.pdf", "event-program.pdf", "faq.pdf", "venue-info.pdf");
        assertThat(result.chunks()).isNotEmpty();
        assertThatThrownBy(() -> result.chunks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnEmptyCatalog() {
        assertThatThrownBy(() -> ingestion(List.of(), document -> stream("content"),
                (document, content) -> List.of(new ExtractedPage(document.name(), 1, "content")),
                new KnowledgeChunker()).ingest())
                .isInstanceOf(IngestionException.class)
                .hasMessage("No canonical PDF document found");
    }

    @Test
    void rejectsAReadFailure() {
        var document = new EventDocument("broken.pdf");

        assertThatThrownBy(() -> ingestion(List.of(document), ignored -> {
            throw new IOException("disk failure");
        }, (ignored, content) -> List.of(new ExtractedPage("broken.pdf", 1, "content")),
                new KnowledgeChunker()).ingest())
                .isInstanceOf(IngestionException.class)
                .hasMessage("Unable to ingest document: broken.pdf")
                .hasRootCauseMessage("disk failure");
    }

    @Test
    void rejectsADocumentWithoutUsefulText() {
        var document = new EventDocument("empty.pdf");

        assertThatThrownBy(() -> ingestion(List.of(document), ignored -> stream("pdf"),
                (ignored, content) -> List.of(), new KnowledgeChunker()).ingest())
                .isInstanceOf(IngestionException.class)
                .hasMessage("Document produced no useful text: empty.pdf");
    }

    @Test
    void rejectsAnIngestionWithoutValidChunks() {
        var document = new EventDocument("no-chunks.pdf");
        var emptyChunker = new KnowledgeChunker() {
            @Override
            public List<KnowledgeChunk> chunk(java.util.Collection<ExtractedPage> pages) {
                return List.of();
            }
        };

        assertThatThrownBy(() -> ingestion(List.of(document), ignored -> stream("pdf"),
                (ignored, content) -> List.of(new ExtractedPage("no-chunks.pdf", 1, "content")),
                emptyChunker).ingest())
                .isInstanceOf(IngestionException.class)
                .hasMessage("Document produced no valid chunks: no-chunks.pdf");
    }

    private static KnowledgeIngestion ingestion(List<EventDocument> documents,
                                                DocumentLoader loader,
                                                DocumentTextExtractor extractor,
                                                KnowledgeChunker chunker) {
        DocumentCatalog catalog = () -> documents;
        return new KnowledgeIngestion(catalog, loader, extractor, chunker);
    }

    private static InputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes());
    }
}

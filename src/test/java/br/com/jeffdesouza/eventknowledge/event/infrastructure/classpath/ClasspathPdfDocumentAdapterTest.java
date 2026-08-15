package br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath;

import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathPdfDocumentAdapterTest {

    @Test
    void catalogsTheFourCanonicalPdfsInDeterministicNameOrder() throws IOException {
        var adapter = new ClasspathPdfDocumentAdapter();

        assertThat(adapter.listDocuments())
                .extracting(EventDocument::name)
                .containsExactly("event-guide.pdf", "event-program.pdf", "faq.pdf", "venue-info.pdf");
    }

    @Test
    void opensCatalogedDocumentsThroughInputStreams() throws IOException {
        var adapter = new ClasspathPdfDocumentAdapter();

        for (EventDocument document : adapter.listDocuments()) {
            try (InputStream content = adapter.open(document)) {
                assertThat(content.readNBytes(4)).containsExactly(0x25, 0x50, 0x44, 0x46);
            }
        }
    }

    @Test
    void doesNotExposeFilesystemResourcesThroughTheDocumentContract() throws IOException {
        var adapter = new ClasspathPdfDocumentAdapter();

        assertThat(adapter).isInstanceOfAny(
                br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog.class,
                br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader.class);
        assertThat(adapter.listDocuments()).isNotEmpty();
        try (InputStream content = adapter.open(new EventDocument("event-guide.pdf"))) {
            assertThat(content).isInstanceOf(InputStream.class);
        }
    }
}

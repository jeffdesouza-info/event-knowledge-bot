package br.com.jeffdesouza.eventknowledge.event.application;

import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentTextExtractor;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreContractsTest {

    @Test
    void constructsEventDocumentSourceAndKnowledgeChunk() {
        var document = new EventDocument(" event-guide.pdf ");
        var source = new SourceReference(document.name(), 2);
        var chunk = new KnowledgeChunk("event-guide.pdf:2:1", "Credenciamento às 7h30.", document.name(), 2, 1);

        assertThat(document.name()).isEqualTo("event-guide.pdf");
        assertThat(source).isEqualTo(new SourceReference("event-guide.pdf", 2));
        assertThat(chunk.id()).isEqualTo("event-guide.pdf:2:1");
        assertThat(chunk.page()).isEqualTo(source.page());
    }

    @Test
    void rejectsInvalidCoreValues() {
        assertThatThrownBy(() -> new EventDocument(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceReference("guide.pdf", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeChunk("id", "text", "guide.pdf", 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractedPage(1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void portsCanBeImplementedWithoutInfrastructureTypes() throws IOException {
        var document = new EventDocument("guide.pdf");
        DocumentCatalog catalog = () -> List.of(document);
        DocumentLoader loader = ignored -> new ByteArrayInputStream("content".getBytes());
        DocumentTextExtractor extractor = (ignored, content) -> List.of(new ExtractedPage(1, content.readAllBytes().toString()));
        KnowledgeStore store = new KnowledgeStore() {
            @Override
            public void replaceAll(java.util.Collection<KnowledgeChunk> chunks) {
            }

            @Override
            public List<KnowledgeChunk> search(String question, int limit) {
                return List.of();
            }
        };

        assertThat(catalog.listDocuments()).containsExactly(document);
        assertThat(loader.open(document).readAllBytes()).isNotEmpty();
        assertThat(extractor.extract(document, loader.open(document))).hasSize(1);
        assertThat(store.search("question", 5)).isEmpty();
    }
}

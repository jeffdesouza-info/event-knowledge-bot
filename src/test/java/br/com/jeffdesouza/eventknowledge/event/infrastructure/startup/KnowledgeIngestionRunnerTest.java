package br.com.jeffdesouza.eventknowledge.event.infrastructure.startup;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunker;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeIngestion;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25.InMemoryBm25KnowledgeStore;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf.PdfBoxDocumentTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIngestionRunnerTest {

    @Test
    void ingestsAndPublishesTheKnowledgeSnapshotWhenTheApplicationRunnerRuns() throws Exception {
        var adapter = new ClasspathPdfDocumentAdapter();
        var ingestion = new KnowledgeIngestion(adapter, adapter,
                new PdfBoxDocumentTextExtractor(), new KnowledgeChunker());
        var store = new InMemoryBm25KnowledgeStore();
        var runner = new KnowledgeIngestionRunner(ingestion, store);

        runner.run(new DefaultApplicationArguments());

        assertThat(store.snapshot().chunks()).isNotEmpty();
    }

    @Test
    void publishesOnlyAfterSuccessfulIngestionAndPreservesPreviousSnapshotOnFailure() {
        var document = new br.com.jeffdesouza.eventknowledge.event.domain.EventDocument("event.pdf");
        AtomicReference<Boolean> shouldFail = new AtomicReference<>(false);
        var ingestion = new KnowledgeIngestion(
                () -> List.of(document),
                ignored -> {
                    if (shouldFail.get()) {
                        throw new IOException("new ingestion failed");
                    }
                    return new ByteArrayInputStream("content".getBytes());
                },
                (ignored, content) -> List.of(new br.com.jeffdesouza.eventknowledge.event.application.ExtractedPage(
                        "event.pdf", 1, "conteúdo")),
                new KnowledgeChunker());
        var store = new InMemoryBm25KnowledgeStore();
        var runner = new KnowledgeIngestionRunner(ingestion, store);

        runner.run(new DefaultApplicationArguments());
        var previousSnapshot = store.snapshot();
        shouldFail.set(true);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(RuntimeException.class);
        assertThat(store.snapshot()).isSameAs(previousSnapshot);
    }
}

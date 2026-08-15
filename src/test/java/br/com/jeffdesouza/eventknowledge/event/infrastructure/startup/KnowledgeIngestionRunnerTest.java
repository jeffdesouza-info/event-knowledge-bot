package br.com.jeffdesouza.eventknowledge.event.infrastructure.startup;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunker;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeIngestion;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf.PdfBoxDocumentTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionRunnerTest {

    @Test
    void executesIngestionWhenTheApplicationRunnerRuns() throws Exception {
        var adapter = new ClasspathPdfDocumentAdapter();
        var ingestion = new KnowledgeIngestion(adapter, adapter,
                new PdfBoxDocumentTextExtractor(), new KnowledgeChunker());
        var runner = new KnowledgeIngestionRunner(ingestion);

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.publishedResult()).isNotNull();
        assertThat(runner.publishedResult().documents()).hasSize(4);
        assertThat(runner.publishedResult().chunks()).isNotEmpty();
    }
}

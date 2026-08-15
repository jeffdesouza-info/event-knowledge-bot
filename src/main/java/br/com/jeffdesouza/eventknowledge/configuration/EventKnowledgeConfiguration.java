package br.com.jeffdesouza.eventknowledge.configuration;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunker;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeIngestion;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentTextExtractor;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf.PdfBoxDocumentTextExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class EventKnowledgeConfiguration {

    @Bean(name = {"classpathPdfDocumentAdapter", "documentCatalog", "documentLoader"})
    ClasspathPdfDocumentAdapter classpathPdfDocumentAdapter() throws IOException {
        return new ClasspathPdfDocumentAdapter();
    }

    @Bean
    DocumentTextExtractor documentTextExtractor() {
        return new PdfBoxDocumentTextExtractor();
    }

    @Bean
    KnowledgeChunker knowledgeChunker(
            @Value("${event-knowledge.chunking.target-size:450}") int targetSize,
            @Value("${event-knowledge.chunking.overlap-size:100}") int overlapSize) {
        return new KnowledgeChunker(targetSize, overlapSize);
    }

    @Bean
    KnowledgeIngestion knowledgeIngestion(DocumentCatalog catalog, DocumentLoader loader,
                                           DocumentTextExtractor extractor, KnowledgeChunker chunker) {
        return new KnowledgeIngestion(catalog, loader, extractor, chunker);
    }
}

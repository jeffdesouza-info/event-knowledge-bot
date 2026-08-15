package br.com.jeffdesouza.eventknowledge.event.application;

import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentTextExtractor;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Orquestra a construção completa do conhecimento a partir dos documentos catalogados. */
public final class KnowledgeIngestion {

    private final DocumentCatalog catalog;
    private final DocumentLoader loader;
    private final DocumentTextExtractor extractor;
    private final KnowledgeChunker chunker;

    public KnowledgeIngestion(DocumentCatalog catalog, DocumentLoader loader,
                              DocumentTextExtractor extractor, KnowledgeChunker chunker) {
        this.catalog = Objects.requireNonNull(catalog, "Document catalog must not be null");
        this.loader = Objects.requireNonNull(loader, "Document loader must not be null");
        this.extractor = Objects.requireNonNull(extractor, "Document extractor must not be null");
        this.chunker = Objects.requireNonNull(chunker, "Knowledge chunker must not be null");
    }

    public IngestionResult ingest() {
        List<EventDocument> documents = catalog.listDocuments();
        if (documents == null || documents.isEmpty()) {
            throw new IngestionException("No canonical PDF document found");
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (EventDocument document : documents) {
            if (document == null) {
                throw new IngestionException("Document catalog contains a null document");
            }
            List<ExtractedPage> pages;
            try (InputStream content = Objects.requireNonNull(loader.open(document),
                    "Document loader returned no content")) {
                pages = extractor.extract(document, content);
            } catch (IOException | RuntimeException exception) {
                if (exception instanceof IngestionException ingestionException) {
                    throw ingestionException;
                }
                throw new IngestionException("Unable to ingest document: " + document.name(), exception);
            }

            if (pages == null || pages.isEmpty()) {
                throw new IngestionException("Document produced no useful text: " + document.name());
            }
            List<KnowledgeChunk> documentChunks = chunker.chunk(pages);
            if (documentChunks == null || documentChunks.isEmpty()) {
                throw new IngestionException("Document produced no valid chunks: " + document.name());
            }
            chunks.addAll(documentChunks);
        }

        if (chunks.isEmpty()) {
            throw new IngestionException("Ingestion produced no valid chunks");
        }
        return new IngestionResult(documents, chunks);
    }
}

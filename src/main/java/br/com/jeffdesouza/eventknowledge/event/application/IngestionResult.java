package br.com.jeffdesouza.eventknowledge.event.application;

import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;

import java.util.List;
import java.util.Objects;

/** Resultado completo e imutável, pronto para uma futura publicação no KnowledgeStore. */
public record IngestionResult(List<EventDocument> documents, List<KnowledgeChunk> chunks) {

    public IngestionResult {
        documents = List.copyOf(Objects.requireNonNull(documents, "Documents must not be null"));
        chunks = List.copyOf(Objects.requireNonNull(chunks, "Chunks must not be null"));
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Ingestion result must contain documents");
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Ingestion result must contain chunks");
        }
    }
}

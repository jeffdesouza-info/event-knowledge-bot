package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Estado publicado do índice BM25; todos os níveis da estrutura são imutáveis. */
public record Bm25Snapshot(
        List<KnowledgeChunk> chunks,
        Map<String, Map<String, Integer>> termFrequencyByChunkId,
        Map<String, Integer> documentFrequency,
        Map<String, Integer> tokenCountByChunkId,
        double averageChunkSize
) {

    public Bm25Snapshot {
        chunks = List.copyOf(Objects.requireNonNull(chunks, "Chunks must not be null"));
        termFrequencyByChunkId = deepCopy(termFrequencyByChunkId, "Term frequency must not be null");
        documentFrequency = copy(documentFrequency, "Document frequency must not be null");
        tokenCountByChunkId = copy(tokenCountByChunkId, "Token counts must not be null");
        if (!Double.isFinite(averageChunkSize) || averageChunkSize < 0) {
            throw new IllegalArgumentException("Average chunk size must be finite and non-negative");
        }
    }

    public int documentCount() {
        return chunks.size();
    }

    public int averageChunkSizeRoundedDown() {
        return (int) averageChunkSize;
    }

    private static <K, V> Map<K, V> copy(Map<K, V> source, String message) {
        return Map.copyOf(Objects.requireNonNull(source, message));
    }

    private static Map<String, Map<String, Integer>> deepCopy(
            Map<String, Map<String, Integer>> source, String message) {
        Objects.requireNonNull(source, message);
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Map.copyOf(Objects.requireNonNull(entry.getValue(), message))));
    }
}

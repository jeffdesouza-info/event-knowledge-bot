package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Store em memória que prepara e publica snapshots textuais para o BM25. */
public final class InMemoryBm25KnowledgeStore implements KnowledgeStore {

    private final Bm25Tokenizer tokenizer;
    private final AtomicReference<Bm25Snapshot> publishedSnapshot;

    public InMemoryBm25KnowledgeStore() {
        this(new Bm25Tokenizer());
    }

    public InMemoryBm25KnowledgeStore(Bm25Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
        this.publishedSnapshot = new AtomicReference<>(emptySnapshot());
    }

    /** Retorna o snapshot atualmente publicado, que não pode ser mutado. */
    public Bm25Snapshot snapshot() {
        return publishedSnapshot.get();
    }

    /** Prepara todo o índice localmente e só então troca a referência publicada. */
    @Override
    public void replaceAll(Collection<KnowledgeChunk> chunks) {
        Bm25Snapshot prepared = prepareSnapshot(chunks);
        publishedSnapshot.set(prepared);
    }

    /** A consulta e o ranking BM25 pertencem à T008. */
    @Override
    public List<KnowledgeChunk> search(String question, int limit) {
        throw new UnsupportedOperationException("BM25 search is implemented in T008");
    }

    private Bm25Snapshot prepareSnapshot(Collection<KnowledgeChunk> chunks) {
        Objects.requireNonNull(chunks, "Chunks must not be null");
        List<KnowledgeChunk> immutableChunks = List.copyOf(chunks);
        if (immutableChunks.isEmpty()) {
            throw new IllegalArgumentException("Snapshot must contain at least one chunk");
        }
        if (immutableChunks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Snapshot must not contain null chunks");
        }

        Set<String> ids = immutableChunks.stream().map(KnowledgeChunk::id).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() != immutableChunks.size()) {
            throw new IllegalArgumentException("Chunk ids must be unique");
        }

        Map<String, Map<String, Integer>> termFrequency = new LinkedHashMap<>();
        Map<String, Integer> documentFrequency = new LinkedHashMap<>();
        Map<String, Integer> tokenCounts = new LinkedHashMap<>();
        int totalTokens = 0;

        for (KnowledgeChunk chunk : immutableChunks) {
            List<String> tokens = tokenizer.tokenize(chunk.text());
            Map<String, Integer> frequencies = new LinkedHashMap<>();
            for (String token : tokens) {
                frequencies.merge(token, 1, Integer::sum);
            }
            termFrequency.put(chunk.id(), frequencies);
            tokenCounts.put(chunk.id(), tokens.size());
            totalTokens += tokens.size();
            frequencies.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }

        return new Bm25Snapshot(immutableChunks, termFrequency, documentFrequency, tokenCounts,
                (double) totalTokens / immutableChunks.size());
    }

    private static Bm25Snapshot emptySnapshot() {
        return new Bm25Snapshot(List.of(), Map.of(), Map.of(), Map.of(), 0.0);
    }
}

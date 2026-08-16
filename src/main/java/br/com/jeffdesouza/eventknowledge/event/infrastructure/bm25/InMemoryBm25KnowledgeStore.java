package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Store em memória que prepara e publica snapshots textuais para o BM25. */
public final class InMemoryBm25KnowledgeStore implements KnowledgeStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryBm25KnowledgeStore.class);
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;
    private static final int MAX_TOP_K = 5;

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

    @Override
    public List<KnowledgeChunk> search(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }

        Bm25Snapshot current = publishedSnapshot.get();
        if (current.chunks().isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = new LinkedHashSet<>(tokenizer.tokenize(question));
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        int resultLimit = Math.min(limit, MAX_TOP_K);
        List<ScoredChunk> rankedChunks = current.chunks().stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk.id(), queryTerms, current)))
                .filter(scored -> scored.score() > 0.0d)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(scored -> scored.chunk().documentName())
                        .thenComparingInt(scored -> scored.chunk().page())
                        .thenComparingInt(scored -> scored.chunk().ordinal())
                        .thenComparing(scored -> scored.chunk().id()))
                .limit(resultLimit)
                .toList();

        if (LOGGER.isDebugEnabled()) {
            for (int index = 0; index < rankedChunks.size(); index++) {
                ScoredChunk candidate = rankedChunks.get(index);
                KnowledgeChunk chunk = candidate.chunk();
                LOGGER.debug("retrieval_candidate rank={} score={} chunkId={} document={} page={} snippet={}",
                        index + 1,
                        candidate.score(),
                        chunk.id(),
                        chunk.documentName(),
                        chunk.page(),
                        snippet(chunk.text()));
            }
        }

        return rankedChunks.stream()
                .map(ScoredChunk::chunk)
                .toList();
    }

    private static String snippet(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120
                ? normalized
                : normalized.substring(0, 120) + "...";
    }

    private static double score(String chunkId, Set<String> queryTerms, Bm25Snapshot snapshot) {
        int documentCount = snapshot.documentCount();
        int chunkSize = snapshot.tokenCountByChunkId().getOrDefault(chunkId, 0);
        double averageChunkSize = snapshot.averageChunkSize();
        double normalization = averageChunkSize == 0.0d
                ? 1.0d
                : 1.0d - B + B * chunkSize / averageChunkSize;

        double score = 0.0d;
        Map<String, Integer> frequencies = snapshot.termFrequencyByChunkId().getOrDefault(chunkId, Map.of());
        for (String term : queryTerms) {
            int documentFrequency = snapshot.documentFrequency().getOrDefault(term, 0);
            int termFrequency = frequencies.getOrDefault(term, 0);
            if (documentFrequency == 0 || termFrequency == 0) {
                continue;
            }
            double inverseDocumentFrequency = Math.log(1.0d
                    + (documentCount - documentFrequency + 0.5d) / (documentFrequency + 0.5d));
            score += inverseDocumentFrequency
                    * (termFrequency * (K1 + 1.0d))
                    / (termFrequency + K1 * normalization);
        }
        return score;
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {
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

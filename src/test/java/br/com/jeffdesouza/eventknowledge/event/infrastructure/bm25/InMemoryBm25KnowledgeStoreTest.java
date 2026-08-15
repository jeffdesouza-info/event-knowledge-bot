package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBm25KnowledgeStoreTest {

    private final Bm25Tokenizer tokenizer = new Bm25Tokenizer();

    @Test
    void tokenizesLowercaseDiacriticsAndUnicodeAlphaNumericRuns() {
        assertThat(tokenizer.tokenize("Árvore Coração 42, naïve co-op")).containsExactly(
                "arvore", "coracao", "42", "naive", "co", "op");
    }

    @Test
    void keepsOriginalTextAndMetadataSeparateFromSearchTokens() {
        KnowledgeChunk chunk = chunk("c1", "Água no São Paulo!", "guide.pdf", 2, 3);

        Bm25Snapshot snapshot = replaceAndSnapshot(store(), List.of(chunk));

        assertThat(snapshot.chunks()).containsExactly(chunk);
        assertThat(snapshot.chunks().getFirst().text()).isEqualTo("Água no São Paulo!");
        assertThat(snapshot.chunks().getFirst().documentName()).isEqualTo("guide.pdf");
        assertThat(snapshot.termFrequencyByChunkId().get("c1")).containsEntry("agua", 1)
                .containsEntry("sao", 1);
        assertThat(snapshot.termFrequencyByChunkId().get("c1")).doesNotContainKey("água");
    }

    @Test
    void calculatesTermFrequencyDocumentFrequencyAndAverageSize() {
        Bm25Snapshot snapshot = replaceAndSnapshot(store(), List.of(
                chunk("c1", "Gato gato azul", "a.pdf", 1, 1),
                chunk("c2", "Gato verde", "b.pdf", 1, 1)));

        assertThat(snapshot.termFrequencyByChunkId().get("c1")).containsEntry("gato", 2);
        assertThat(snapshot.documentFrequency()).containsEntry("gato", 2)
                .containsEntry("azul", 1).containsEntry("verde", 1);
        assertThat(snapshot.tokenCountByChunkId()).containsEntry("c1", 3).containsEntry("c2", 2);
        assertThat(snapshot.averageChunkSize()).isEqualTo(2.5);
    }

    @Test
    void keepsPublishedSnapshotImmutable() {
        Bm25Snapshot snapshot = replaceAndSnapshot(store(), List.of(chunk("c1", "texto", "a.pdf", 1, 1)));

        assertThatThrownBy(() -> snapshot.chunks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.documentFrequency().put("novo", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.termFrequencyByChunkId().get("c1").put("novo", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void atomicallyPublishesOnlyAValidPreparedSnapshot() {
        InMemoryBm25KnowledgeStore store = store();
        store.replaceAll(List.of(chunk("old", "conteúdo anterior", "old.pdf", 1, 1)));
        Bm25Snapshot previous = store.snapshot();

        store.replaceAll(List.of(chunk("new", "conteúdo novo", "new.pdf", 2, 1)));
        assertThat(store.snapshot()).isNotSameAs(previous);
        assertThat(store.snapshot().chunks()).extracting(KnowledgeChunk::id).containsExactly("new");
    }

    @Test
    void doesNotPublishPartialSnapshotWhenPreparationFails() {
        InMemoryBm25KnowledgeStore store = store();
        store.replaceAll(List.of(chunk("old", "conteúdo anterior", "old.pdf", 1, 1)));
        Bm25Snapshot previous = store.snapshot();

        assertThatThrownBy(() -> store.replaceAll(List.of(
                chunk("new", "conteúdo novo", "new.pdf", 2, 1),
                chunk("new", "ids duplicados", "new.pdf", 2, 2))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(store.snapshot()).isSameAs(previous);
        assertThat(store.snapshot().chunks()).extracting(KnowledgeChunk::id).containsExactly("old");
    }

    @Test
    void ranksByPositiveBm25ScoreAndKeepsOriginalChunkData() {
        InMemoryBm25KnowledgeStore store = store();
        KnowledgeChunk strongest = chunk("strong", "Raro raro", "strong.pdf", 2, 1);
        KnowledgeChunk weaker = chunk("weak", "Raro comum", "weak.pdf", 1, 1);
        store.replaceAll(List.of(weaker, strongest, chunk("other", "Comum", "other.pdf", 1, 1)));

        assertThat(store.search("RÁRO", 5)).containsExactly(strongest, weaker);
        assertThat(store.search("RÁRO", 5).getFirst().text()).isEqualTo("Raro raro");
        assertThat(store.search("RÁRO", 5).getFirst().documentName()).isEqualTo("strong.pdf");
    }

    @Test
    void respectsRequestedLimitAndConfiguredMaximumTopK() {
        InMemoryBm25KnowledgeStore store = store();
        List<KnowledgeChunk> chunks = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(index -> chunk("c" + index, "alvo", "file-" + index + ".pdf", 1, 1))
                .toList();
        store.replaceAll(chunks);

        assertThat(store.search("alvo", 2)).hasSize(2);
        assertThat(store.search("alvo", 99)).hasSize(5);
    }

    @Test
    void excludesChunksWithoutPositiveEvidenceAndReturnsEmptyWhenNothingMatches() {
        InMemoryBm25KnowledgeStore store = store();
        store.replaceAll(List.of(
                chunk("match", "Acesso Premium", "a.pdf", 1, 1),
                chunk("unrelated", "Estacionamento", "b.pdf", 1, 1)));

        assertThat(store.search("Premium", 5)).extracting(KnowledgeChunk::id)
                .containsExactly("match");
        assertThat(store.search("transmissão ao vivo", 5)).isEmpty();
        assertThat(store.search("!!!", 5)).isEmpty();
    }

    @Test
    void usesDeterministicFilePageAndOrdinalTieBreakers() {
        InMemoryBm25KnowledgeStore store = store();
        KnowledgeChunk laterPage = chunk("b", "termo", "a.pdf", 2, 1);
        KnowledgeChunk earlierFile = chunk("c", "termo", "a.pdf", 1, 2);
        KnowledgeChunk first = chunk("a", "termo", "a.pdf", 1, 1);
        store.replaceAll(List.of(laterPage, first, earlierFile));

        assertThat(store.search("termo", 5)).containsExactly(first, earlierFile, laterPage);
    }

    @Test
    void tokenizesQuestionWithTheSameStrategyAsIndexedText() {
        InMemoryBm25KnowledgeStore store = store();
        KnowledgeChunk chunk = chunk("c1", "A entrada Premium fica no Portão B", "venue.pdf", 1, 1);
        store.replaceAll(List.of(chunk));

        assertThat(store.search("portao PREMIUM", 5)).containsExactly(chunk);
    }

    private static InMemoryBm25KnowledgeStore store() {
        return new InMemoryBm25KnowledgeStore();
    }

    private static Bm25Snapshot replaceAndSnapshot(InMemoryBm25KnowledgeStore store,
                                                   List<KnowledgeChunk> chunks) {
        store.replaceAll(chunks);
        return store.snapshot();
    }

    private static KnowledgeChunk chunk(String id, String text, String document, int page, int ordinal) {
        return new KnowledgeChunk(id, text, document, page, ordinal);
    }
}

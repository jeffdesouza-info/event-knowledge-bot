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

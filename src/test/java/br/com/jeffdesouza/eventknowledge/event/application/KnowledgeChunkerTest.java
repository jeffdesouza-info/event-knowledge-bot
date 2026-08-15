package br.com.jeffdesouza.eventknowledge.event.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeChunkerTest {

    @Test
    void neverCrossesPageBoundariesAndPreservesMetadata() {
        var pages = List.of(
                new ExtractedPage("guide.pdf", 2, "A".repeat(60)),
                new ExtractedPage("guide.pdf", 4, "B".repeat(60)));

        var chunks = new KnowledgeChunker(40, 10).chunk(pages);

        assertThat(chunks).extracting(KnowledgeChunk::page).containsExactly(2, 2, 4, 4);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.documentName()).isEqualTo("guide.pdf");
            assertThat(chunk.text()).doesNotContain(chunk.page() == 2 ? "B" : "A");
        });
        assertThat(chunks).extracting(KnowledgeChunk::id)
                .containsExactly("guide.pdf:2:1", "guide.pdf:2:2", "guide.pdf:4:1", "guide.pdf:4:2");
        assertThat(chunks).extracting(KnowledgeChunk::ordinal).containsExactly(1, 2, 1, 2);
    }

    @Test
    void overlapsAdjacentChunksByConfiguredCharacterCount() {
        String text = "0123456789".repeat(8);

        var chunks = new KnowledgeChunker(30, 10).chunk(new ExtractedPage("guide.pdf", 1, text));

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(1).text()).startsWith(chunks.get(0).text().substring(20));
        assertThat(chunks.get(2).text()).startsWith(chunks.get(1).text().substring(20));
    }

    @Test
    void prefersParagraphLineAndSentenceBoundariesBeforeForcedCut() {
        var paragraphText = "A".repeat(24) + "\n\n" + "B".repeat(24) + "\n\n" + "C".repeat(24);
        var sentenceText = "A".repeat(24) + ". Próxima frase começa aqui.";

        var paragraphChunks = new KnowledgeChunker(30, 5).chunk(new ExtractedPage("guide.pdf", 1, paragraphText));
        var sentenceChunks = new KnowledgeChunker(30, 5).chunk(new ExtractedPage("guide.pdf", 1, sentenceText));

        assertThat(paragraphChunks.get(0).text()).endsWith("A".repeat(24));
        assertThat(sentenceChunks.get(0).text()).endsWith("A".repeat(24) + ".");
    }

    @Test
    void fallsBackToExactTargetWhenThereIsNoNaturalBoundary() {
        var chunks = new KnowledgeChunker(20, 5)
                .chunk(new ExtractedPage("guide.pdf", 1, "X".repeat(45)));

        assertThat(chunks).extracting(KnowledgeChunk::text)
                .containsExactly("X".repeat(20), "X".repeat(20), "X".repeat(15));
    }

    @Test
    void producesStableIdsForTheSameNormalizedPage() {
        var page = new ExtractedPage("guide.pdf", 3, "conteúdo normalizado");
        var chunker = new KnowledgeChunker();

        assertThat(chunker.chunk(page)).isEqualTo(chunker.chunk(page));
        assertThat(chunker.chunk(page).get(0))
                .extracting(KnowledgeChunk::id, KnowledgeChunk::documentName, KnowledgeChunk::page,
                        KnowledgeChunk::ordinal, KnowledgeChunk::text)
                .containsExactly("guide.pdf:3:1", "guide.pdf", 3, 1, "conteúdo normalizado");
    }

    @Test
    void requiresDocumentNameForStableIdentity() {
        assertThatThrownBy(() -> new KnowledgeChunker().chunk(new ExtractedPage(1, "text")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document name");
    }
}

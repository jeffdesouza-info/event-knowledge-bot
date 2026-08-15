package br.com.jeffdesouza.eventknowledge.event.infrastructure.startup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25.InMemoryBm25KnowledgeStore;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KnowledgeIngestionStartupTest {

    @Autowired
    private InMemoryBm25KnowledgeStore store;

    @Test
    void publishesTheKnowledgeSnapshotDuringApplicationStartup() {
        assertThat(store.snapshot().chunks()).isNotEmpty();
    }
}

package br.com.jeffdesouza.eventknowledge.event.infrastructure.startup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KnowledgeIngestionStartupTest {

    @Autowired
    private KnowledgeIngestionRunner runner;

    @Test
    void publishesTheIngestionResultDuringApplicationStartup() {
        assertThat(runner.publishedResult()).isNotNull();
        assertThat(runner.publishedResult().documents()).hasSize(4);
        assertThat(runner.publishedResult().chunks()).isNotEmpty();
    }
}

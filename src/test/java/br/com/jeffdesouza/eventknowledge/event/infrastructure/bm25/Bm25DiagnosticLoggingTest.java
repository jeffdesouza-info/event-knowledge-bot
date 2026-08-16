package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25DiagnosticLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(InMemoryBm25KnowledgeStore.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void logsRankedCandidateMetadataAndOnlyALimitedSnippetAtDebug() {
        String fullText = "Termo importante " + "conteudo sensivel ".repeat(30);
        KnowledgeChunk chunk = new KnowledgeChunk("guide:1:1", fullText, "event-guide.pdf", 3, 1);
        InMemoryBm25KnowledgeStore store = new InMemoryBm25KnowledgeStore();
        store.replaceAll(List.of(chunk));

        assertThat(store.search("importante", 5)).containsExactly(chunk);

        ILoggingEvent event = appender.list.stream()
                .filter(loggingEvent -> loggingEvent.getFormattedMessage().startsWith("retrieval_candidate"))
                .findFirst()
                .orElseThrow();
        String message = event.getFormattedMessage();
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(message).contains("rank=1", "chunkId=guide:1:1", "document=event-guide.pdf", "page=3");
        assertThat(message).contains("snippet=Termo importante");
        assertThat(message).doesNotContain(fullText);
        assertThat(message).endsWith("...");
    }
}

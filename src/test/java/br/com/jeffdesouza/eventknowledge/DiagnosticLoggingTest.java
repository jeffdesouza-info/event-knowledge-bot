package br.com.jeffdesouza.eventknowledge;

import br.com.jeffdesouza.eventknowledge.assistant.application.AnswerEventQuestion;
import br.com.jeffdesouza.eventknowledge.assistant.application.GeneratedAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticLoggingTest {

    private final Logger answerLogger = logger(AnswerEventQuestion.class);
    private ListAppender<ILoggingEvent> answerAppender;

    @BeforeEach
    void attachAppender() {
        answerAppender = new ListAppender<>();
        answerAppender.start();
        answerLogger.addAppender(answerAppender);
        answerLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        answerLogger.detachAppender(answerAppender);
        answerLogger.setLevel(null);
    }

    @Test
    void logsQueryLifecycleCountsOutcomesAndDurationsAtInfoWithoutQuestionContent() {
        KnowledgeChunk chunk = new KnowledgeChunk(
                "guide:1:1", "Credenciamento Ã s 07:30.", "event-guide.pdf", 1, 1);
        AnswerGenerator generator = (question, context) ->
                new GeneratedAnswer(AnswerStatus.ANSWERED, "Resposta", List.of(chunk.id()));

        new AnswerEventQuestion(store(List.of(chunk)), generator).answer("pergunta com segredo");

        List<String> messages = answerAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(message -> message.startsWith("question_cycle_started"));
        assertThat(messages).anyMatch(message -> message.matches(
                "retrieval_completed outcome=CONTEXT_FOUND chunkCount=1 durationMs=\\d+"));
        assertThat(messages).anyMatch(message -> message.matches("qa_completed outcome=ANSWERED durationMs=\\d+"));
        assertThat(messages).anyMatch(message -> message.matches("question_cycle_completed outcome=ANSWERED totalDurationMs=\\d+"));
        assertThat(messages).noneMatch(message -> message.contains("pergunta com segredo"));
        assertThat(answerAppender.list).allMatch(event -> event.getLevel() == Level.INFO);
    }

    private static Logger logger(Class<?> type) {
        return (Logger) LoggerFactory.getLogger(type);
    }

    private static KnowledgeStore store(List<KnowledgeChunk> chunks) {
        return new KnowledgeStore() {
            @Override
            public void replaceAll(Collection<KnowledgeChunk> ignored) {
            }

            @Override
            public List<KnowledgeChunk> search(String question, int limit) {
                return chunks;
            }
        };
    }
}

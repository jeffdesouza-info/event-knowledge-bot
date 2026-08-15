package br.com.jeffdesouza.eventknowledge.assistant.application;

import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerEventQuestionTest {

    private static final KnowledgeChunk GUIDE =
            new KnowledgeChunk("guide:1:1", "Credenciamento às 7h30.", "event-guide.pdf", 1, 1);
    private static final KnowledgeChunk FAQ =
            new KnowledgeChunk("faq:2:1", "A entrada é pelo Portão B.", "faq.pdf", 2, 1);

    @Test
    void invalidQuestionDoesNotTriggerRetrievalOrGeneration() {
        AtomicInteger retrievals = new AtomicInteger();
        AtomicInteger generations = new AtomicInteger();
        AnswerEventQuestion useCase = new AnswerEventQuestion(
                searchCountingStore(retrievals), generatingCountingAnswerGenerator(generations));

        assertThatThrownBy(() -> useCase.answer("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retrievals).hasValue(0);
        assertThat(generations).hasValue(0);
    }

    @Test
    void questionAboveConfiguredLimitDoesNotTriggerRetrievalOrGeneration() {
        AtomicInteger retrievals = new AtomicInteger();
        AtomicInteger generations = new AtomicInteger();
        AnswerEventQuestion useCase = new AnswerEventQuestion(
                searchCountingStore(retrievals), generatingCountingAnswerGenerator(generations), 5, 10);

        assertThatThrownBy(() -> useCase.answer("12345678901"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retrievals).hasValue(0);
        assertThat(generations).hasValue(0);
    }

    @Test
    void noChunksReturnsFixedInsufficientInformationWithoutCallingGenerator() {
        AtomicInteger generations = new AtomicInteger();
        AnswerEventQuestion useCase = new AnswerEventQuestion(
                store((question, limit) -> List.of()), generatingCountingAnswerGenerator(generations));

        var answer = useCase.answer("Qual é o horário?");

        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT_INFORMATION);
        assertThat(answer.text()).isEqualTo(AnswerEventQuestion.INFORMATION_NOT_FOUND);
        assertThat(answer.sources()).isEmpty();
        assertThat(generations).hasValue(0);
    }

    @Test
    void retrievesWithConfiguredTopKAndSendsTheRetrievedChunksToGenerator() {
        AtomicInteger receivedLimit = new AtomicInteger();
        AtomicReference<List<KnowledgeChunk>> receivedContext = new AtomicReference<>();
        AnswerGenerator generator = (question, context) -> {
            receivedContext.set(context);
            return new GeneratedAnswer(AnswerStatus.ANSWERED, "Resposta", List.of(GUIDE.id()));
        };
        KnowledgeStore store = store((question, limit) -> {
            receivedLimit.set(limit);
            return List.of(GUIDE, FAQ);
        });

        var answer = new AnswerEventQuestion(store, generator, 3, 500).answer("Onde é?");

        assertThat(receivedLimit).hasValue(3);
        assertThat(receivedContext).hasValue(List.of(GUIDE, FAQ));
        assertThat(answer.status()).isEqualTo(AnswerStatus.ANSWERED);
    }

    @Test
    void sourcesAreProjectedOnlyFromLocalChunkMetadata() {
        AnswerGenerator generator = (question, context) -> new GeneratedAnswer(
                AnswerStatus.ANSWERED,
                "Resposta com uma fonte inventada pelo modelo",
                List.of("invented-id", FAQ.id(), GUIDE.id(), FAQ.id()));
        AnswerEventQuestion useCase = new AnswerEventQuestion(
                store((question, limit) -> List.of(GUIDE, FAQ)), generator);

        var answer = useCase.answer("Qual informação existe?");

        assertThat(answer.sources()).extracting("documentName", "page")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("event-guide.pdf", 1),
                        org.assertj.core.groups.Tuple.tuple("faq.pdf", 2));
    }

    @Test
    void generatorIsCalledOnlyWhenContextExists() {
        AtomicInteger generations = new AtomicInteger();
        AnswerGenerator generator = (question, context) -> {
            generations.incrementAndGet();
            return new GeneratedAnswer(AnswerStatus.ANSWERED, "Resposta", List.of());
        };

        new AnswerEventQuestion(store((question, limit) -> List.of(GUIDE)), generator)
                .answer("Pergunta válida");

        assertThat(generations).hasValue(1);
    }

    private static KnowledgeStore searchCountingStore(AtomicInteger retrievals) {
        return store((question, limit) -> {
            retrievals.incrementAndGet();
            return List.of(GUIDE);
        });
    }

    private static KnowledgeStore store(BiFunction<String, Integer, List<KnowledgeChunk>> search) {
        return new KnowledgeStore() {
            @Override
            public void replaceAll(Collection<KnowledgeChunk> chunks) {
            }

            @Override
            public List<KnowledgeChunk> search(String question, int limit) {
                return search.apply(question, limit);
            }
        };
    }

    private static AnswerGenerator generatingCountingAnswerGenerator(AtomicInteger generations) {
        return (question, context) -> {
            generations.incrementAndGet();
            return new GeneratedAnswer(AnswerStatus.ANSWERED, "Resposta", List.of());
        };
    }
}

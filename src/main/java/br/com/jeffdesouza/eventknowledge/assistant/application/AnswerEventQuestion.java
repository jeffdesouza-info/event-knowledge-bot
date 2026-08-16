package br.com.jeffdesouza.eventknowledge.assistant.application;

import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventQuestion;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Orquestra validação, recuperação e geração da resposta sobre o evento. */
public final class AnswerEventQuestion {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnswerEventQuestion.class);
    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_MAX_QUESTION_LENGTH = 500;
    public static final String INFORMATION_NOT_FOUND =
            "Não encontrei essa informação nos documentos disponíveis sobre o evento.";

    private final KnowledgeStore knowledgeStore;
    private final AnswerGenerator answerGenerator;
    private final int topK;
    private final int maxQuestionLength;

    public AnswerEventQuestion(KnowledgeStore knowledgeStore, AnswerGenerator answerGenerator) {
        this(knowledgeStore, answerGenerator, DEFAULT_TOP_K, DEFAULT_MAX_QUESTION_LENGTH);
    }

    public AnswerEventQuestion(KnowledgeStore knowledgeStore, AnswerGenerator answerGenerator,
                               int topK, int maxQuestionLength) {
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "Knowledge store must not be null");
        this.answerGenerator = Objects.requireNonNull(answerGenerator, "Answer generator must not be null");
        if (topK <= 0) {
            throw new IllegalArgumentException("Top-k must be positive");
        }
        if (maxQuestionLength <= 0) {
            throw new IllegalArgumentException("Maximum question length must be positive");
        }
        this.topK = topK;
        this.maxQuestionLength = maxQuestionLength;
    }

    public EventAnswer answer(String questionText) {
        long cycleStartedAt = System.nanoTime();
        LOGGER.info("question_cycle_started");
        try {
            EventQuestion question = validateQuestion(questionText);

            long retrievalStartedAt = System.nanoTime();
            List<KnowledgeChunk> context = knowledgeStore.search(question.text(), topK);
            LOGGER.info("retrieval_completed outcome={} chunkCount={} durationMs={}",
                    context.isEmpty() ? "NO_EVIDENCE" : "CONTEXT_FOUND",
                    context.size(),
                    elapsedMillis(retrievalStartedAt));
            if (context.isEmpty()) {
                LOGGER.info("question_cycle_completed outcome=INSUFFICIENT_INFORMATION totalDurationMs={}",
                        elapsedMillis(cycleStartedAt));
                return new EventAnswer(AnswerStatus.INSUFFICIENT_INFORMATION, INFORMATION_NOT_FOUND, List.of());
            }

            long qaStartedAt = System.nanoTime();
            GeneratedAnswer generatedAnswer;
            try {
                generatedAnswer = answerGenerator.generate(question, context);
                validateGeneratedAnswer(generatedAnswer, context);
            } catch (RuntimeException exception) {
                LOGGER.info("qa_completed outcome=ERROR durationMs={}", elapsedMillis(qaStartedAt));
                throw exception;
            }
            LOGGER.info("qa_completed outcome={} durationMs={}",
                    generatedAnswer.status(), elapsedMillis(qaStartedAt));

            if (generatedAnswer.status() == AnswerStatus.INSUFFICIENT_INFORMATION) {
                LOGGER.info("question_cycle_completed outcome=INSUFFICIENT_INFORMATION totalDurationMs={}",
                        elapsedMillis(cycleStartedAt));
                return new EventAnswer(AnswerStatus.INSUFFICIENT_INFORMATION, INFORMATION_NOT_FOUND, List.of());
            }

            EventAnswer answer = new EventAnswer(AnswerStatus.ANSWERED, generatedAnswer.text(),
                    projectSources(context, generatedAnswer.evidenceChunkIds()));
            LOGGER.info("question_cycle_completed outcome=ANSWERED totalDurationMs={}",
                    elapsedMillis(cycleStartedAt));
            return answer;
        } catch (RuntimeException exception) {
            LOGGER.info("question_cycle_completed outcome=ERROR totalDurationMs={}",
                    elapsedMillis(cycleStartedAt));
            throw exception;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private EventQuestion validateQuestion(String questionText) {
        EventQuestion question = new EventQuestion(questionText);
        if (question.text().length() > maxQuestionLength) {
            throw new IllegalArgumentException("Question exceeds the maximum configured length");
        }
        return question;
    }

    private List<SourceReference> projectSources(List<KnowledgeChunk> context, List<String> evidenceChunkIds) {
        Set<String> evidenceIds = new LinkedHashSet<>(evidenceChunkIds);
        return context.stream()
                .filter(chunk -> evidenceIds.contains(chunk.id()))
                .map(chunk -> new SourceReference(chunk.documentName(), chunk.page()))
                .distinct()
                .toList();
    }

    private void validateGeneratedAnswer(GeneratedAnswer generatedAnswer, List<KnowledgeChunk> context) {
        if (generatedAnswer == null) {
            throw new AnswerGenerationException("Generated answer must not be null");
        }

        Set<String> contextIds = context.stream()
                .map(KnowledgeChunk::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> evidenceChunkIds = generatedAnswer.evidenceChunkIds();
        Set<String> uniqueEvidenceIds = new HashSet<>(evidenceChunkIds);

        if (uniqueEvidenceIds.size() != evidenceChunkIds.size()) {
            throw new AnswerGenerationException("Generated answer contains duplicate evidence chunk ids");
        }
        if (!contextIds.containsAll(uniqueEvidenceIds)) {
            throw new AnswerGenerationException("Generated answer contains evidence outside the retrieved context");
        }

        if (generatedAnswer.status() == AnswerStatus.ANSWERED) {
            if (generatedAnswer.text() == null || generatedAnswer.text().isBlank()) {
                throw new AnswerGenerationException("Answered result must contain a non-blank answer");
            }
            if (evidenceChunkIds.isEmpty()) {
                throw new AnswerGenerationException("Answered result must contain evidence");
            }
            return;
        }

        if (!evidenceChunkIds.isEmpty()) {
            throw new AnswerGenerationException("Insufficient information result must not contain evidence");
        }
    }
}

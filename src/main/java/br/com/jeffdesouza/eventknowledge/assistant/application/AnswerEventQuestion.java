package br.com.jeffdesouza.eventknowledge.assistant.application;

import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventQuestion;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Orquestra validação, recuperação e geração da resposta sobre o evento. */
public final class AnswerEventQuestion {

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
        EventQuestion question = validateQuestion(questionText);
        List<KnowledgeChunk> context = knowledgeStore.search(question.text(), topK);
        if (context.isEmpty()) {
            return new EventAnswer(AnswerStatus.INSUFFICIENT_INFORMATION, INFORMATION_NOT_FOUND, List.of());
        }

        GeneratedAnswer generatedAnswer = Objects.requireNonNull(
                answerGenerator.generate(question, context), "Generated answer must not be null");
        return new EventAnswer(
                generatedAnswer.status(),
                generatedAnswer.text(),
                projectSources(context, generatedAnswer.evidenceChunkIds()));
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
}

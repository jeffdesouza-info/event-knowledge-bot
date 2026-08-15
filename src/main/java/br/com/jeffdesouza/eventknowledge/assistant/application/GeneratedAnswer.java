package br.com.jeffdesouza.eventknowledge.assistant.application;

import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;

import java.util.List;
import java.util.Objects;

/** Resultado técnico do gerador; IDs de evidência serão validados pela aplicação. */
public record GeneratedAnswer(AnswerStatus status, String text, List<String> evidenceChunkIds) {

    public GeneratedAnswer {
        Objects.requireNonNull(status, "Status must not be null");
        evidenceChunkIds = List.copyOf(Objects.requireNonNull(evidenceChunkIds, "Evidence ids must not be null"));
    }
}

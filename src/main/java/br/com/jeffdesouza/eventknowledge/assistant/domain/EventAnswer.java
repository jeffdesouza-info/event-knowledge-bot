package br.com.jeffdesouza.eventknowledge.assistant.domain;

import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;

import java.util.List;
import java.util.Objects;

/** Resposta apresentada ao participante, com as fontes que a sustentam. */
public record EventAnswer(AnswerStatus status, String text, List<SourceReference> sources) {

    public EventAnswer {
        Objects.requireNonNull(status, "Status must not be null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Answer text must not be blank");
        }
        sources = List.copyOf(Objects.requireNonNull(sources, "Sources must not be null"));
    }
}

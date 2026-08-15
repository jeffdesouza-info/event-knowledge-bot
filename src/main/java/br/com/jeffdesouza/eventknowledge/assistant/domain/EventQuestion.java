package br.com.jeffdesouza.eventknowledge.assistant.domain;

/** Pergunta feita pelo participante sobre o evento. */
public record EventQuestion(String text) {

    public EventQuestion {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        text = text.trim();
    }
}

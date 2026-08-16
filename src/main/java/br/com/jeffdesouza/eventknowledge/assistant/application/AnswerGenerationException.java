package br.com.jeffdesouza.eventknowledge.assistant.application;

/** Falha na geração ou na validação determinística da resposta do provedor. */
public final class AnswerGenerationException extends IllegalStateException {

    public AnswerGenerationException(String message) {
        super(message);
    }

    public AnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

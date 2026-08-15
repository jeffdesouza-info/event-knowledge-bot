package br.com.jeffdesouza.eventknowledge.event.application;

/** Falha irrecuperável na preparação do corpus durante o startup. */
public final class IngestionException extends IllegalStateException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}

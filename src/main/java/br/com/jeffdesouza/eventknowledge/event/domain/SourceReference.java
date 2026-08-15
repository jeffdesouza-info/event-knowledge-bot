package br.com.jeffdesouza.eventknowledge.event.domain;

/** Referência rastreável a uma posição de um documento do evento. */
public record SourceReference(String documentName, int page) {

    public SourceReference {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        documentName = documentName.trim();
    }
}

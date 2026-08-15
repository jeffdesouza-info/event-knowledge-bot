package br.com.jeffdesouza.eventknowledge.event.domain;

/** Identidade e metadados essenciais de um documento associado ao evento. */
public record EventDocument(String name) {

    public EventDocument {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }
        name = name.trim();
    }
}

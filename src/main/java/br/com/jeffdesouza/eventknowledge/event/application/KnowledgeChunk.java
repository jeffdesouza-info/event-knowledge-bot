package br.com.jeffdesouza.eventknowledge.event.application;

import java.util.Objects;

/** Unidade técnica de recuperação; não representa uma entidade do domínio do evento. */
public record KnowledgeChunk(
        String id,
        String text,
        String documentName,
        int page,
        int ordinal
) {

    public KnowledgeChunk {
        requireText(id, "Chunk id");
        requireText(text, "Chunk text");
        requireText(documentName, "Document name");
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (ordinal < 1) {
            throw new IllegalArgumentException("Ordinal must be positive");
        }
        id = id.trim();
        text = text.trim();
        documentName = documentName.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}

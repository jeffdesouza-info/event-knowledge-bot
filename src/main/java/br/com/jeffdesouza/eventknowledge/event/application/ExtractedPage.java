package br.com.jeffdesouza.eventknowledge.event.application;

/** Texto extraído de uma página, preservando a unidade necessária ao chunking. */
public record ExtractedPage(String documentName, int page, String text) {

    public ExtractedPage(int page, String text) {
        this(null, page, text);
    }

    public ExtractedPage {
        if (documentName != null && documentName.isBlank()) {
            throw new IllegalArgumentException("Document name must not be blank");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Page text must not be blank");
        }
        documentName = documentName == null ? null : documentName.trim();
        text = text.trim();
    }
}

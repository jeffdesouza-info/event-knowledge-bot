package br.com.jeffdesouza.eventknowledge.event.application;

/** Texto extraído de uma página, preservando a unidade necessária ao chunking. */
public record ExtractedPage(int page, String text) {

    public ExtractedPage {
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Page text must not be blank");
        }
        text = text.trim();
    }
}

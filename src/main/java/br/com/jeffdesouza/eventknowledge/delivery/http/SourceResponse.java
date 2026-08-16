package br.com.jeffdesouza.eventknowledge.delivery.http;

/** Fonte pública associada a uma resposta. */
public record SourceResponse(String documentName, int page, String url) {
}

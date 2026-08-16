package br.com.jeffdesouza.eventknowledge.delivery.http;

/** Erro HTTP público e estável, sem detalhes internos. */
public record ApiErrorResponse(String code, String message) {
}

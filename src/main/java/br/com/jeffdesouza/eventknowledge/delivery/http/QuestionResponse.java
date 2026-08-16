package br.com.jeffdesouza.eventknowledge.delivery.http;

import java.util.List;

/** Contrato de saída HTTP, independente dos tipos internos da aplicação. */
public record QuestionResponse(String answer, List<SourceResponse> sources) {

    public QuestionResponse {
        sources = List.copyOf(sources);
    }
}

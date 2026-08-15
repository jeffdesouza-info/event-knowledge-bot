package br.com.jeffdesouza.eventknowledge.assistant.application.port;

import br.com.jeffdesouza.eventknowledge.assistant.application.GeneratedAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventQuestion;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;

import java.util.List;

/** Port para gerar uma resposta a partir da pergunta e do contexto recuperado. */
public interface AnswerGenerator {

    GeneratedAnswer generate(EventQuestion question, List<KnowledgeChunk> context);
}

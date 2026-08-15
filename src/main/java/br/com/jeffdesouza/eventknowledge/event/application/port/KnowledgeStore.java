package br.com.jeffdesouza.eventknowledge.event.application.port;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;

import java.util.Collection;
import java.util.List;

/** Port de publicação e recuperação do conhecimento processado. */
public interface KnowledgeStore {

    void replaceAll(Collection<KnowledgeChunk> chunks);

    List<KnowledgeChunk> search(String question, int limit);
}

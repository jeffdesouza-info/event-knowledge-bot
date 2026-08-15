package br.com.jeffdesouza.eventknowledge.event.application.port;

import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;

import java.util.List;

/** Port de consulta do catálogo de documentos disponíveis para o evento. */
public interface DocumentCatalog {

    List<EventDocument> listDocuments();
}

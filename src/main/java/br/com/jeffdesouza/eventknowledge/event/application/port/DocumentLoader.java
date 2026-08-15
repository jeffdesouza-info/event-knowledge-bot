package br.com.jeffdesouza.eventknowledge.event.application.port;

import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;

import java.io.IOException;
import java.io.InputStream;

/** Port para abrir o conteúdo de um documento sem expor sua tecnologia de origem. */
public interface DocumentLoader {

    InputStream open(EventDocument document) throws IOException;
}

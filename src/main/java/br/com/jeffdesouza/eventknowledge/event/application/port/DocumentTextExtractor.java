package br.com.jeffdesouza.eventknowledge.event.application.port;

import br.com.jeffdesouza.eventknowledge.event.application.ExtractedPage;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Port para transformar um documento em texto paginado. */
public interface DocumentTextExtractor {

    List<ExtractedPage> extract(EventDocument document, InputStream content) throws IOException;
}

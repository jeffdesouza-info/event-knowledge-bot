package br.com.jeffdesouza.eventknowledge.delivery.http;

import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Expõe somente os documentos PDF presentes no catálogo carregado. */
@RestController
@RequestMapping(DocumentController.DOCUMENTS_PATH)
public class DocumentController {

    static final String DOCUMENTS_PATH = "/documents/";

    private final DocumentLoader documentLoader;
    private final Map<String, EventDocument> documentsByName;

    public DocumentController(DocumentCatalog documentCatalog, DocumentLoader documentLoader) {
        this.documentLoader = documentLoader;

        Map<String, EventDocument> catalog = new LinkedHashMap<>();
        for (EventDocument document : documentCatalog.listDocuments()) {
            if (document == null || !document.name().endsWith(".pdf")) {
                continue;
            }
            if (catalog.putIfAbsent(document.name(), document) != null) {
                throw new IllegalStateException("Duplicate catalog document: " + document.name());
            }
        }
        this.documentsByName = Map.copyOf(catalog);
    }

    @GetMapping("{filename:.+}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String filename) {
        EventDocument document = documentsByName.get(filename);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(documentLoader.open(document)));
        } catch (IOException | RuntimeException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    static String urlFor(String documentName) {
        return DOCUMENTS_PATH + documentName;
    }
}

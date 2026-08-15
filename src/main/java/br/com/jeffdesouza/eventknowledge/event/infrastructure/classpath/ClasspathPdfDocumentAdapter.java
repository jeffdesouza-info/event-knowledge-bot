package br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath;

import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentCatalog;
import br.com.jeffdesouza.eventknowledge.event.application.port.DocumentLoader;
import br.com.jeffdesouza.eventknowledge.event.domain.EventDocument;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapter que cataloga e abre os PDFs canônicos empacotados no classpath. */
public final class ClasspathPdfDocumentAdapter implements DocumentCatalog, DocumentLoader {

    static final String PDF_RESOURCE_PATTERN = "classpath*:/documents/pdfs/*.pdf";

    private final Map<String, Resource> resourcesByName;
    private final List<EventDocument> documents;

    public ClasspathPdfDocumentAdapter() throws IOException {
        this(new PathMatchingResourcePatternResolver());
    }

    ClasspathPdfDocumentAdapter(ResourcePatternResolver resolver) throws IOException {
        Resource[] resources = resolver.getResources(PDF_RESOURCE_PATTERN);
        Map<String, Resource> catalog = Arrays.stream(resources)
                .sorted(Comparator.comparing(Resource::getFilename,
                        Comparator.nullsLast(String::compareTo)))
                .collect(LinkedHashMap::new, (map, resource) -> {
                    String name = resource.getFilename();
                    if (name == null || name.isBlank()) {
                        throw new IllegalStateException("Classpath PDF resource has no filename");
                    }
                    if (map.putIfAbsent(name, resource) != null) {
                        throw new IllegalStateException("Duplicate classpath PDF resource: " + name);
                    }
                }, Map::putAll);

        this.resourcesByName = Map.copyOf(catalog);
        this.documents = this.resourcesByName.keySet().stream()
                .sorted()
                .map(EventDocument::new)
                .toList();
    }

    @Override
    public List<EventDocument> listDocuments() {
        return documents;
    }

    @Override
    public InputStream open(EventDocument document) throws IOException {
        Resource resource = resourcesByName.get(document.name());
        if (resource == null) {
            throw new IOException("Document is not part of the classpath catalog: " + document.name());
        }
        return resource.getInputStream();
    }
}

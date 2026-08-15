package br.com.jeffdesouza.eventknowledge.event.infrastructure.startup;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeIngestion;
import br.com.jeffdesouza.eventknowledge.event.application.IngestionResult;
import br.com.jeffdesouza.eventknowledge.event.application.port.KnowledgeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


/** Executa a ingestão completa antes de a aplicação ser considerada iniciada. */
@Component
public final class KnowledgeIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);

    private final KnowledgeIngestion ingestion;
    private final KnowledgeStore knowledgeStore;

    public KnowledgeIngestionRunner(KnowledgeIngestion ingestion, KnowledgeStore knowledgeStore) {
        this.ingestion = ingestion;
        this.knowledgeStore = knowledgeStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        IngestionResult result = ingestion.ingest();
        knowledgeStore.replaceAll(result.chunks());
        log.info("Knowledge ingestion completed: documents={}, chunks={}, names={}",
                result.documents().size(), result.chunks().size(),
                result.documents().stream().map(document -> document.name()).toList());
    }
}

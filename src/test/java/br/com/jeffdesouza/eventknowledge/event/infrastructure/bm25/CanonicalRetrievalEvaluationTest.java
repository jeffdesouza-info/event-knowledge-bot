package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunker;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeIngestion;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter;
import br.com.jeffdesouza.eventknowledge.event.infrastructure.pdf.PdfBoxDocumentTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Avaliação determinística do baseline BM25 contra os quatro PDFs canônicos. */
class CanonicalRetrievalEvaluationTest {

    private static final int TOP_K = 5;

    @Test
    void achievesHitRateAt5ForTheElevenPositiveCanonicalQuestions() throws IOException {
        List<EvaluationCase> cases = positiveCases();
        InMemoryBm25KnowledgeStore store = indexedCanonicalCorpus();

        long hits = 0;
        List<String> failures = new java.util.ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            List<KnowledgeChunk> topFive = store.search(evaluationCase.question(), TOP_K);
            boolean hit = topFive.stream().anyMatch(evaluationCase::matchesExpectedEvidence);
            if (hit) {
                hits++;
            } else {
                failures.add("#" + evaluationCase.number() + " " + evaluationCase.question()
                        + " top-5=" + describe(topFive));
            }
            System.out.printf("Caso #%d [%s]: %s%n", evaluationCase.number(), hit ? "HIT" : "MISS",
                    evaluationCase.question());
            System.out.printf("  top-5: %s%n", describe(topFive));
        }

        double hitRateAt5 = (double) hits / cases.size();
        assertThat(failures)
                .as("Casos positivos sem evidência esperada no top-5")
                .isEmpty();
        assertThat(hitRateAt5)
                .as("HitRate@5 dos 11 casos positivos")
                .isEqualTo(1.0d);
    }

    @Test
    void recordsTheNonAnswerableControlCaseWithoutRequiringEmptyRetrieval() throws IOException {
        EvaluationCase control = new EvaluationCase(12, "O evento terá transmissão ao vivo?");
        List<KnowledgeChunk> topFive = indexedCanonicalCorpus().search(control.question(), TOP_K);

        System.out.printf("Caso 12 (controle negativo) top-5: %s%n", describe(topFive));
        assertThat(topFive).as("O controle negativo serve para inspeção; não exige lista vazia").isNotNull();
    }

    private static InMemoryBm25KnowledgeStore indexedCanonicalCorpus() throws IOException {
        ClasspathPdfDocumentAdapter documents = new ClasspathPdfDocumentAdapter();
        var ingestion = new KnowledgeIngestion(
                documents,
                documents,
                new PdfBoxDocumentTextExtractor(),
                new KnowledgeChunker());
        InMemoryBm25KnowledgeStore store = new InMemoryBm25KnowledgeStore();
        store.replaceAll(ingestion.ingest().chunks());
        return store;
    }

    private static List<EvaluationCase> positiveCases() {
        return List.of(
                new EvaluationCase(1, "Que horas começa o credenciamento?", Set.of("event-guide.pdf", "faq.pdf"), Set.of("credenciamento", "07")),
                new EvaluationCase(2, "Posso entrar em uma sessão depois que ela começou?", Set.of("faq.pdf"), Set.of("sessão", "assentos")),
                new EvaluationCase(3, "Onde fica a entrada Premium?", Set.of("venue-info.pdf"), Set.of("portão", "premium", "leste")),
                new EvaluationCase(4, "Qual é o horário do evento na quinta-feira?", Set.of("event-guide.pdf"), Set.of("quinta-feira", "08:00", "19:00")),
                new EvaluationCase(5, "Quando acontece RAG na Prática?", Set.of("event-program.pdf"), Set.of("rag", "11:25", "12:15")),
                new EvaluationCase(6, "Participantes Premium precisam de inscrição adicional nos workshops?", Set.of("faq.pdf", "event-guide.pdf"), Set.of("premium", "inscrição", "capacidade")),
                new EvaluationCase(7, "Qual é o nome da rede Wi-Fi?", Set.of("faq.pdf", "venue-info.pdf"), Set.of("futuretech", "guest")),
                new EvaluationCase(8, "O estacionamento é gratuito?", Set.of("venue-info.pdf"), Set.of("estacionamento", "pago", "420")),
                new EvaluationCase(9, "O que fazer se eu perder a credencial?", Set.of("faq.pdf", "event-guide.pdf"), Set.of("credencial", "documento", "oficial")),
                new EvaluationCase(10, "Há guarda-volumes para malas grandes?", Set.of("faq.pdf"), Set.of("malas", "grandes", "aceitas")),
                new EvaluationCase(11, "O local possui acesso para pessoas com mobilidade reduzida?", Set.of("venue-info.pdf"), Set.of("acesso sem degraus", "elevadores"))
        );
    }

    private static String describe(List<KnowledgeChunk> chunks) {
        return chunks.stream()
                .map(chunk -> "%s [id=%s, página=%d, ordinal=%d]".formatted(
                        chunk.documentName(), chunk.id(), chunk.page(), chunk.ordinal()
                                ) + " text=" + chunk.text())
                .toList()
                .toString();
    }

    private record EvaluationCase(int number, String question, Set<String> documents, Set<String> evidenceTerms) {
        private EvaluationCase(int number, String question) {
            this(number, question, Set.of(), Set.of());
        }

        private boolean matchesExpectedEvidence(KnowledgeChunk chunk) {
            String normalizedText = normalize(chunk.text());
            return documents.contains(chunk.documentName())
                    && evidenceTerms.stream().allMatch(term -> normalizedText.contains(normalize(term)));
        }

        private static String normalize(String value) {
            return Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
        }
    }
}

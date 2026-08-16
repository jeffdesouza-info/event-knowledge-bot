package br.com.jeffdesouza.eventknowledge;

import br.com.jeffdesouza.eventknowledge.assistant.application.GeneratedAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventQuestion;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EventKnowledgeBotIntegrationTest.DeterministicAnswerGeneratorConfiguration.class)
class EventKnowledgeBotIntegrationTest {

    private static final String CANONICAL_DOCUMENT = "event-guide.pdf";

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void demonstratesIntegratedLocalFlowWithoutOpenAiOrExternalServices() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> page = get("/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("<html lang=\"pt-BR\">")
                .contains("id=\"question-form\"");

        HttpResponse<String> answered = postQuestion("Que horas começa o credenciamento?");
        assertThat(answered.statusCode()).isEqualTo(200);
        assertThat(answered.body()).contains("O credenciamento começa às 07:30.")
                .contains("\"documentName\":\"event-guide.pdf\"")
                .contains("\"url\":\"/documents/event-guide.pdf\"");

        HttpResponse<String> insufficient = postQuestion("O evento terá transmissão ao vivo?");
        assertThat(insufficient.statusCode()).isEqualTo(200);
        assertThat(insufficient.body())
                .contains("Não encontrei essa informação nos documentos disponíveis sobre o evento.")
                .contains("\"sources\":[]");

        HttpResponse<byte[]> document = getBytes("/documents/" + CANONICAL_DOCUMENT);
        assertThat(document.statusCode()).isEqualTo(200);
        assertThat(document.headers().firstValue("content-type")).hasValue("application/pdf");
        assertThat(document.body()).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> postQuestion(String question) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri("/api/questions"))
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"" + question + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicAnswerGeneratorConfiguration {

        @Bean
        @Primary
        AnswerGenerator deterministicAnswerGenerator() {
            return new DeterministicAnswerGenerator();
        }
    }

    static final class DeterministicAnswerGenerator implements AnswerGenerator {

        @Override
        public GeneratedAnswer generate(EventQuestion question, List<KnowledgeChunk> context) {
            return context.stream()
                    .filter(chunk -> question.text().toLowerCase().contains("credenciamento"))
                    .filter(chunk -> chunk.text().contains("07:30"))
                    .findFirst()
                    .map(chunk -> new GeneratedAnswer(
                            AnswerStatus.ANSWERED,
                            "O credenciamento começa às 07:30.",
                            List.of(chunk.id())))
                    .orElseGet(() -> new GeneratedAnswer(
                            AnswerStatus.INSUFFICIENT_INFORMATION,
                            null,
                            List.of()));
        }
    }
}

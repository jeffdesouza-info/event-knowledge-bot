package br.com.jeffdesouza.eventknowledge.delivery.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StaticPageSmokeTest {

    @Test
    void servesPortuguesePageWithQuestionForm() throws IOException {
        String html = resource("static/index.html");

        assertThat(html).contains("<html lang=\"pt-BR\">")
                .contains("id=\"question-form\"")
                .contains("id=\"question\"")
                .contains("/styles.css")
                .contains("/app.js");
    }

    @Test
    void javascriptPostsQuestionAndCoversAnswerSourcesLoadingAndErrors() throws IOException {
        String javascript = resource("static/app.js");

        assertThat(javascript).contains("fetch(\"/api/questions\"")
                .contains("JSON.stringify({ question })")
                .contains("setLoading(true)")
                .contains("setLoading(false)")
                .contains("answer.textContent")
                .contains("link.textContent")
                .contains("link.href")
                .contains("status === 400")
                .contains("status === 503")
                .contains("showError")
                .contains("sources.replaceChildren()");
    }

    @Test
    void rendersExternalContentWithoutHtmlInterpretationOrFrontendDependencies() throws IOException {
        String javascript = resource("static/app.js");

        assertThat(javascript).contains("textContent")
                .doesNotContain("innerHTML")
                .doesNotContain("insertAdjacentHTML")
                .doesNotContain("<script src=\"http")
                .doesNotContain("import ")
                .doesNotContain("require(");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

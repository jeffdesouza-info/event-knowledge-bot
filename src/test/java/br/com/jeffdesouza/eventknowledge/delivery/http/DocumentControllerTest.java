package br.com.jeffdesouza.eventknowledge.delivery.http;

import br.com.jeffdesouza.eventknowledge.event.infrastructure.classpath.ClasspathPdfDocumentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerTest {

    private static final String[] CANONICAL_DOCUMENTS = {
            "event-guide.pdf", "event-program.pdf", "faq.pdf", "venue-info.pdf"
    };

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        var adapter = new ClasspathPdfDocumentAdapter();
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(adapter, adapter)).build();
    }

    @Test
    void downloadsEveryCanonicalPdfAsPdf() throws Exception {
        for (String document : CANONICAL_DOCUMENTS) {
            mockMvc.perform(get("/documents/{filename}", document))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII)));
        }
    }

    @Test
    void returnsPdfContentForCanonicalDocuments() throws Exception {
        for (String document : CANONICAL_DOCUMENTS) {
            mockMvc.perform(get("/documents/{filename}", document))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("application/pdf")))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII)));
        }
    }

    @Test
    void returnsNotFoundForUnknownOrUnauthorizedNames() throws Exception {
        mockMvc.perform(get("/documents/missing.pdf")).andExpect(status().isNotFound());
        mockMvc.perform(get("/documents/application.yml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/documents/event-guide.txt")).andExpect(status().isNotFound());
        mockMvc.perform(get("/documents/..//path-traversal.pdf")).andExpect(status().isNotFound());
        mockMvc.perform(get("/documents/%2e%2e%2fapplication.yml")).andExpect(status().isNotFound());
    }
}

package br.com.jeffdesouza.eventknowledge.delivery.http;

import br.com.jeffdesouza.eventknowledge.assistant.application.AnswerEventQuestion;
import br.com.jeffdesouza.eventknowledge.assistant.application.AnswerGenerationException;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventAnswer;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private AnswerEventQuestion answerEventQuestion;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new QuestionController(answerEventQuestion, 500))
                .setControllerAdvice(new HttpErrorHandler())
                .build();
    }

    @Test
    void returnsAnswerAndSourcesForValidQuestion() throws Exception {
        when(answerEventQuestion.answer("Que horas começa o credenciamento?"))
                .thenReturn(new EventAnswer(AnswerStatus.ANSWERED, "O credenciamento começa às 7h30.",
                        List.of(new SourceReference("event-guide.pdf", 1))));

        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"Que horas começa o credenciamento?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "answer": "O credenciamento começa às 7h30.",
                          "sources": [{
                            "documentName": "event-guide.pdf",
                            "page": 1,
                            "url": "/documents/event-guide.pdf"
                          }]
                        }
                        """));
    }

    @Test
    void returnsFixedInsufficientInformationWithEmptySources() throws Exception {
        when(answerEventQuestion.answer(anyString()))
                .thenReturn(new EventAnswer(AnswerStatus.INSUFFICIENT_INFORMATION,
                        AnswerEventQuestion.INFORMATION_NOT_FOUND, List.of()));

        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"O evento terá transmissão ao vivo?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AnswerEventQuestion.INFORMATION_NOT_FOUND))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void rejectsMissingQuestionWithStablePtBrError() throws Exception {
        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION"))
                .andExpect(jsonPath("$.message").value("A pergunta deve ser informada e conter até o limite permitido de caracteres."));

        verifyNoInteractions(answerEventQuestion);
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION"))
                .andExpect(jsonPath("$.message").value("A pergunta deve ser informada e conter até o limite permitido de caracteres."));

        verifyNoInteractions(answerEventQuestion);
    }

    @Test
    void rejectsQuestionAboveConfiguredLimit() throws Exception {
        String question = "a".repeat(501);

        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION"))
                .andExpect(jsonPath("$.message").value("A pergunta deve ser informada e conter até o limite permitido de caracteres."));

        verifyNoInteractions(answerEventQuestion);
    }

    @Test
    void mapsAnswerGenerationFailureToSafeServiceUnavailableError() throws Exception {
        doThrow(new AnswerGenerationException("payload interno da OpenAI; chave secreta"))
                .when(answerEventQuestion).answer(anyString());

        mockMvc.perform(post("/api/questions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"question\":\"Pergunta válida\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANSWER_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("O serviço de respostas está indisponível no momento. Tente novamente mais tarde."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OpenAI"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("chave"))))
                .andExpect(jsonPath("$.trace").doesNotExist());

        verify(answerEventQuestion).answer("Pergunta válida");
    }
}

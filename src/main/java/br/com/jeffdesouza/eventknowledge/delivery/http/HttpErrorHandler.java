package br.com.jeffdesouza.eventknowledge.delivery.http;

import br.com.jeffdesouza.eventknowledge.assistant.application.AnswerGenerationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HttpErrorHandler {

    private static final String INVALID_QUESTION_CODE = "INVALID_QUESTION";
    private static final String INVALID_REQUEST_MESSAGE = "A pergunta deve ser informada e conter até o limite permitido de caracteres.";
    private static final String GENERATION_UNAVAILABLE_MESSAGE = "O serviço de respostas está indisponível no momento. Tente novamente mais tarde.";

    @ExceptionHandler(InvalidQuestionException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidQuestion() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(INVALID_QUESTION_CODE, INVALID_REQUEST_MESSAGE));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedRequest() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", "A requisição deve conter um JSON válido com uma pergunta."));
    }

    @ExceptionHandler({AnswerGenerationException.class,
            br.com.jeffdesouza.eventknowledge.assistant.infrastructure.openai.OpenAiResponsesAnswerGenerator.AnswerGenerationException.class})
    ResponseEntity<ApiErrorResponse> handleAnswerGenerationFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse("ANSWER_SERVICE_UNAVAILABLE", GENERATION_UNAVAILABLE_MESSAGE));
    }
}

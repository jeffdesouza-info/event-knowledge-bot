package br.com.jeffdesouza.eventknowledge.delivery.http;

import br.com.jeffdesouza.eventknowledge.assistant.application.AnswerEventQuestion;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventAnswer;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final AnswerEventQuestion answerEventQuestion;
    private final int maxQuestionLength;

    public QuestionController(AnswerEventQuestion answerEventQuestion,
                              @Value("${event-knowledge.question.max-length:500}") int maxQuestionLength) {
        this.answerEventQuestion = answerEventQuestion;
        this.maxQuestionLength = maxQuestionLength;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> answer(@RequestBody QuestionRequest request) {
        String question = request == null ? null : request.question();
        validateQuestion(question);

        EventAnswer answer = answerEventQuestion.answer(question);
        return ResponseEntity.ok(toResponse(answer));
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank() || question.trim().length() > maxQuestionLength) {
            throw new InvalidQuestionException();
        }
    }

    private QuestionResponse toResponse(EventAnswer answer) {
        List<SourceResponse> sources = answer.sources().stream()
                .map(this::toSourceResponse)
                .toList();
        return new QuestionResponse(answer.text(), sources);
    }

    private SourceResponse toSourceResponse(SourceReference source) {
        return new SourceResponse(source.documentName(), source.page(), DocumentController.urlFor(source.documentName()));
    }
}

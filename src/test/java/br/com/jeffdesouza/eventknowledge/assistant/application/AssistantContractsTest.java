package br.com.jeffdesouza.eventknowledge.assistant.application;

import br.com.jeffdesouza.eventknowledge.assistant.application.port.AnswerGenerator;
import br.com.jeffdesouza.eventknowledge.assistant.domain.AnswerStatus;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventAnswer;
import br.com.jeffdesouza.eventknowledge.assistant.domain.EventQuestion;
import br.com.jeffdesouza.eventknowledge.event.application.KnowledgeChunk;
import br.com.jeffdesouza.eventknowledge.event.domain.SourceReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantContractsTest {

    @Test
    void constructsQuestionAnswerAndAnswerGeneratorContract() {
        var question = new EventQuestion(" Que horas começa o credenciamento? ");
        var source = new SourceReference("event-guide.pdf", 1);
        var answer = new EventAnswer(AnswerStatus.ANSWERED, "O credenciamento começa às 7h30.", List.of(source));
        var chunk = new KnowledgeChunk("event-guide.pdf:1:1", "Credenciamento às 7h30.", "event-guide.pdf", 1, 1);
        AnswerGenerator generator = (receivedQuestion, context) -> new GeneratedAnswer(
                AnswerStatus.ANSWERED, "O credenciamento começa às 7h30.", List.of(context.getFirst().id()));

        var generated = generator.generate(question, List.of(chunk));

        assertThat(question.text()).isEqualTo("Que horas começa o credenciamento?");
        assertThat(answer.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(answer.sources()).containsExactly(source);
        assertThat(generated.evidenceChunkIds()).containsExactly(chunk.id());
    }

    @Test
    void preservesGeneratedEvidenceAsImmutableContractData() {
        var ids = new java.util.ArrayList<>(List.of("guide:1:1"));
        var generated = new GeneratedAnswer(AnswerStatus.INSUFFICIENT_INFORMATION, null, ids);
        ids.add("guide:1:2");

        assertThat(generated.evidenceChunkIds()).containsExactly("guide:1:1");
        assertThatThrownBy(() -> generated.evidenceChunkIds().add("guide:1:2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankQuestionAndAnswer() {
        assertThatThrownBy(() -> new EventQuestion(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventAnswer(AnswerStatus.ANSWERED, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

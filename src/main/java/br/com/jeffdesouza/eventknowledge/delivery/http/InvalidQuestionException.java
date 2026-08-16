package br.com.jeffdesouza.eventknowledge.delivery.http;

final class InvalidQuestionException extends RuntimeException {
    InvalidQuestionException() {
        super("A pergunta deve ser informada e conter até o limite permitido de caracteres.");
    }
}

package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tokeniza texto de forma determinística para o índice textual BM25. */
public final class Bm25Tokenizer {

    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        String withoutDiacritics = removeDiacritics(text.toLowerCase(Locale.ROOT));
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        withoutDiacritics.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                token.appendCodePoint(codePoint);
            } else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        });
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }

    private static String removeDiacritics(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK
                        && Character.getType(codePoint) != Character.COMBINING_SPACING_MARK
                        && Character.getType(codePoint) != Character.ENCLOSING_MARK)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}

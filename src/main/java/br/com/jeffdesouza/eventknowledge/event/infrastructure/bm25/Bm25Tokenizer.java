package br.com.jeffdesouza.eventknowledge.event.infrastructure.bm25;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Locale;

/** Tokeniza texto de forma determinística para o índice textual BM25. */
public final class Bm25Tokenizer {

    private static final Set<String> PORTUGUESE_STOPWORDS = Set.of(
            "a", "ao", "aos", "as", "da", "das", "de", "do", "dos", "e", "em", "entre",
            "essa", "essas", "esse", "esses", "esta", "estas", "este", "estes", "for", "ha",
            "na", "nas", "no", "nos", "num", "numa", "o", "os", "ou", "para", "pela",
            "pelas", "pelo", "pelos", "por", "qual", "quais", "se", "sua", "suas", "seu",
            "seus", "um", "uma", "umas", "uns", "que", "quando", "onde");

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
                addNormalizedToken(tokens, token.toString());
                token.setLength(0);
            }
        });
        if (!token.isEmpty()) {
            addNormalizedToken(tokens, token.toString());
        }
        return List.copyOf(tokens);
    }

    private static void addNormalizedToken(List<String> tokens, String token) {
        if (PORTUGUESE_STOPWORDS.contains(token)) {
            return;
        }
        tokens.add(token);
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

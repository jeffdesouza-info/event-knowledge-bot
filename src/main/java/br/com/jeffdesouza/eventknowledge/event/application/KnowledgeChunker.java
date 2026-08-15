package br.com.jeffdesouza.eventknowledge.event.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Divide texto já normalizado em unidades de recuperação sem cruzar páginas. */
public final class KnowledgeChunker {

    public static final int DEFAULT_TARGET_SIZE = 450;
    public static final int DEFAULT_OVERLAP_SIZE = 100;

    private final int targetSize;
    private final int overlapSize;

    public KnowledgeChunker() {
        this(DEFAULT_TARGET_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    public KnowledgeChunker(int targetSize, int overlapSize) {
        if (targetSize < 1) {
            throw new IllegalArgumentException("Target size must be positive");
        }
        if (overlapSize < 0 || overlapSize >= targetSize) {
            throw new IllegalArgumentException("Overlap must be non-negative and smaller than target size");
        }
        this.targetSize = targetSize;
        this.overlapSize = overlapSize;
    }

    public List<KnowledgeChunk> chunk(ExtractedPage page) {
        Objects.requireNonNull(page, "Page must not be null");
        if (page.documentName() == null || page.documentName().isBlank()) {
            throw new IllegalArgumentException("Document name is required to create stable chunk ids");
        }

        String text = page.text();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int start = 0;
        int ordinal = 1;

        while (start < text.length()) {
            int end = chooseEnd(text, start);
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                chunks.add(new KnowledgeChunk(
                        page.documentName() + ":" + page.page() + ":" + ordinal,
                        chunkText,
                        page.documentName(),
                        page.page(),
                        ordinal));
                ordinal++;
            }

            if (end >= text.length()) {
                break;
            }
            int nextStart = Math.max(start + 1, end - overlapSize);
            start = nextStart;
        }

        return List.copyOf(chunks);
    }

    public List<KnowledgeChunk> chunk(Collection<ExtractedPage> pages) {
        Objects.requireNonNull(pages, "Pages must not be null");
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (ExtractedPage page : pages) {
            chunks.addAll(chunk(page));
        }
        return List.copyOf(chunks);
    }

    private int chooseEnd(String text, int start) {
        int targetEnd = Math.min(start + targetSize, text.length());
        if (targetEnd == text.length()) {
            return targetEnd;
        }

        int minimumNaturalEnd = Math.min(start + Math.max(1, targetSize / 2), targetEnd);
        int paragraphEnd = lastParagraphBoundary(text, start, minimumNaturalEnd, targetEnd);
        if (paragraphEnd > start) {
            return paragraphEnd;
        }
        int lineEnd = lastLineBoundary(text, start, minimumNaturalEnd, targetEnd);
        if (lineEnd > start) {
            return lineEnd;
        }
        int sentenceEnd = lastSentenceBoundary(text, start, minimumNaturalEnd, targetEnd);
        return sentenceEnd > start ? sentenceEnd : targetEnd;
    }

    private static int lastParagraphBoundary(String text, int start, int minimum, int maximum) {
        int boundary = -1;
        for (int index = minimum; index <= maximum - 1; index++) {
            if (text.charAt(index) == '\n' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                boundary = index + 2;
            }
        }
        return boundary;
    }

    private static int lastLineBoundary(String text, int start, int minimum, int maximum) {
        int boundary = -1;
        for (int index = minimum; index <= maximum - 1; index++) {
            if (text.charAt(index) == '\n') {
                boundary = index + 1;
            }
        }
        return boundary;
    }

    private static int lastSentenceBoundary(String text, int start, int minimum, int maximum) {
        int boundary = -1;
        for (int index = minimum; index < maximum; index++) {
            char character = text.charAt(index);
            if ((character == '.' || character == '!' || character == '?')
                    && (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)))) {
                boundary = index + 1;
            }
        }
        return boundary;
    }
}

package io.jclaw.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits text into chunks based on a {@link ChunkingStrategy}.
 */
public class TextChunker {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\\n\\s*\\n");

    /**
     * Split text into chunks according to the given strategy.
     *
     * @param text     the text to split
     * @param strategy chunking configuration
     * @return list of text chunks, never empty if input is non-blank
     */
    public List<String> chunk(String text, ChunkingStrategy strategy) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return switch (strategy.mode()) {
            case FIXED_SIZE -> chunkFixedSize(text, strategy);
            case SENTENCE -> chunkByBoundary(text, strategy, SENTENCE_BOUNDARY);
            case PARAGRAPH -> chunkByBoundary(text, strategy, PARAGRAPH_BOUNDARY);
        };
    }

    private List<String> chunkFixedSize(String text, ChunkingStrategy strategy) {
        List<String> chunks = new ArrayList<>();
        int step = strategy.maxChunkSize() - strategy.overlap();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + strategy.maxChunkSize(), text.length());
            chunks.add(text.substring(start, end).strip());
            if (end >= text.length()) break;
            start += step;
        }
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    private List<String> chunkByBoundary(String text, ChunkingStrategy strategy, Pattern boundary) {
        String[] segments = boundary.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String segment : segments) {
            String trimmed = segment.strip();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() + 1 > strategy.maxChunkSize() && !current.isEmpty()) {
                chunks.add(current.toString().strip());

                // Overlap: keep trailing text from previous chunk
                if (strategy.overlap() > 0 && current.length() > strategy.overlap()) {
                    String overlapText = current.substring(current.length() - strategy.overlap());
                    current = new StringBuilder(overlapText);
                } else {
                    current = new StringBuilder();
                }
            }

            if (!current.isEmpty()) current.append(" ");
            current.append(trimmed);
        }

        if (!current.isEmpty()) {
            String last = current.toString().strip();
            if (!last.isBlank()) chunks.add(last);
        }

        return chunks;
    }
}

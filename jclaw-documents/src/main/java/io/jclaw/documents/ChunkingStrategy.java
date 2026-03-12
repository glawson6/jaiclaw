package io.jclaw.documents;

/**
 * Strategy for splitting document text into chunks for embedding.
 *
 * @param maxChunkSize maximum characters per chunk
 * @param overlap      number of characters to overlap between consecutive chunks
 * @param mode         chunking mode
 */
public record ChunkingStrategy(
        int maxChunkSize,
        int overlap,
        Mode mode
) {
    public enum Mode {
        /** Split at fixed character boundaries */
        FIXED_SIZE,
        /** Split at sentence boundaries */
        SENTENCE,
        /** Split at paragraph boundaries (double newline) */
        PARAGRAPH
    }

    public ChunkingStrategy {
        if (maxChunkSize <= 0) throw new IllegalArgumentException("maxChunkSize must be positive");
        if (overlap < 0) throw new IllegalArgumentException("overlap must not be negative");
        if (overlap >= maxChunkSize) throw new IllegalArgumentException("overlap must be less than maxChunkSize");
    }

    /** Default strategy: 1000 chars, 100 overlap, paragraph mode. */
    public static ChunkingStrategy defaults() {
        return new ChunkingStrategy(1000, 100, Mode.PARAGRAPH);
    }

    public static ChunkingStrategy fixedSize(int size, int overlap) {
        return new ChunkingStrategy(size, overlap, Mode.FIXED_SIZE);
    }

    public static ChunkingStrategy sentences(int maxSize, int overlap) {
        return new ChunkingStrategy(maxSize, overlap, Mode.SENTENCE);
    }

    public static ChunkingStrategy paragraphs(int maxSize, int overlap) {
        return new ChunkingStrategy(maxSize, overlap, Mode.PARAGRAPH);
    }
}

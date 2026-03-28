package io.jaiclaw.core.model;

/**
 * Result of a context compaction operation.
 *
 * @param summary          the generated summary text
 * @param originalTokens   estimated token count before compaction
 * @param compactedTokens  estimated token count after compaction
 * @param messagesRemoved  number of messages replaced by the summary
 */
public record CompactionResult(
        String summary,
        int originalTokens,
        int compactedTokens,
        int messagesRemoved
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String summary;
        private int originalTokens;
        private int compactedTokens;
        private int messagesRemoved;

        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder originalTokens(int originalTokens) { this.originalTokens = originalTokens; return this; }
        public Builder compactedTokens(int compactedTokens) { this.compactedTokens = compactedTokens; return this; }
        public Builder messagesRemoved(int messagesRemoved) { this.messagesRemoved = messagesRemoved; return this; }

        public CompactionResult build() {
            return new CompactionResult(summary, originalTokens, compactedTokens, messagesRemoved);
        }
    }
}

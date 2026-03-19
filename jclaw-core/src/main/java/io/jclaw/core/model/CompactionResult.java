package io.jclaw.core.model;

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
}

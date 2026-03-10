package io.jclaw.core.model;

public record TokenUsage(
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheWriteTokens
) {
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}

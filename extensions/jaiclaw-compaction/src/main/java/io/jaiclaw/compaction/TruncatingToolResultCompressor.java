package io.jaiclaw.compaction;

import io.jaiclaw.core.tool.ToolResult;

/**
 * Default compressor that truncates tool result content to fit within a token budget.
 * Uses the approximation of 4 characters per token for truncation length.
 */
public class TruncatingToolResultCompressor implements ToolResultCompressor {

    private static final int CHARS_PER_TOKEN = 4;
    private static final String TRUNCATION_MARKER = "\n... [truncated]";

    @Override
    public ToolResult.Success compress(ToolResult.Success result, int budgetTokens) {
        String content = result.content();
        if (content == null || content.isEmpty()) return result;

        int maxChars = budgetTokens * CHARS_PER_TOKEN;
        if (content.length() <= maxChars) return result;

        String truncated = content.substring(0, maxChars - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
        return new ToolResult.Success(truncated, result.metadata());
    }

    @Override
    public boolean supports(ToolResult.Success result) {
        return result.content() != null && !result.content().isEmpty();
    }
}

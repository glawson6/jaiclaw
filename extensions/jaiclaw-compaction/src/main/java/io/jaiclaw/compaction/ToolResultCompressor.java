package io.jaiclaw.compaction;

import io.jaiclaw.core.tool.ToolResult;

/**
 * SPI for compressing tool results to fit within a token budget.
 */
public interface ToolResultCompressor {

    ToolResult.Success compress(ToolResult.Success result, int budgetTokens);

    boolean supports(ToolResult.Success result);
}

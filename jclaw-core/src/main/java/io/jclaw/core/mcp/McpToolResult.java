package io.jclaw.core.mcp;

import java.util.Map;

/**
 * Result of executing an MCP tool.
 *
 * @param content    text content returned to the MCP client
 * @param isError    whether this result represents an error
 * @param metadata   additional metadata (optional)
 */
public record McpToolResult(
        String content,
        boolean isError,
        Map<String, Object> metadata
) {
    public McpToolResult {
        if (metadata == null) metadata = Map.of();
    }

    public static McpToolResult success(String content) {
        return new McpToolResult(content, false, Map.of());
    }

    public static McpToolResult error(String message) {
        return new McpToolResult(message, true, Map.of());
    }
}

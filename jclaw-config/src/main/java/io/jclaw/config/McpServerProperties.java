package io.jclaw.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration for external MCP server connections, bound from {@code jclaw.mcp-servers}.
 * Each entry maps a server name to its transport configuration.
 *
 * <p>Supported transport types:
 * <ul>
 *   <li>{@code stdio} — subprocess with JSON-RPC over stdin/stdout</li>
 *   <li>{@code sse} — Server-Sent Events connection</li>
 *   <li>{@code http} — Streamable HTTP transport</li>
 * </ul>
 */
public record McpServerProperties(Map<String, McpServerEntry> servers) {

    public static final McpServerProperties DEFAULT = new McpServerProperties(Map.of());

    public McpServerProperties {
        if (servers == null) servers = Map.of();
    }

    /**
     * Configuration for a single MCP server connection.
     *
     * @param description human-readable description
     * @param type        transport type: "stdio", "sse", or "http"
     * @param command     command to execute (stdio only)
     * @param args        command arguments (stdio only)
     * @param url         endpoint URL (sse/http only)
     * @param authToken   Bearer token for authentication (http only, nullable)
     * @param enabled     whether this server connection is active (default true)
     */
    public record McpServerEntry(
            String description,
            String type,
            String command,
            List<String> args,
            String url,
            String authToken,
            Boolean enabled
    ) {
        public McpServerEntry {
            if (args == null) args = List.of();
            if (enabled == null) enabled = Boolean.TRUE;
        }
    }
}

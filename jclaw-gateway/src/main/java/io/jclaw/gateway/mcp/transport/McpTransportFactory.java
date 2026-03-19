package io.jclaw.gateway.mcp.transport;

import io.jclaw.config.McpServerProperties.McpServerEntry;
import io.jclaw.core.mcp.McpToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that creates the appropriate MCP transport provider based on
 * the configured transport type (stdio, sse, http).
 */
public final class McpTransportFactory {

    private static final Logger log = LoggerFactory.getLogger(McpTransportFactory.class);

    private McpTransportFactory() {}

    /**
     * Create and start/connect/initialize an MCP tool provider for the given configuration.
     *
     * @param name  server name (config key)
     * @param entry server configuration entry
     * @return a fully initialized McpToolProvider
     * @throws Exception if the provider cannot be started or connected
     */
    public static McpToolProvider create(String name, McpServerEntry entry) throws Exception {
        String type = entry.type();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("MCP server '" + name + "' has no transport type configured");
        }

        return switch (type.toLowerCase()) {
            case "stdio" -> {
                if (entry.command() == null || entry.command().isBlank()) {
                    throw new IllegalArgumentException("MCP server '" + name + "' (stdio) requires a command");
                }
                StdioMcpToolProvider provider = new StdioMcpToolProvider(
                        name, entry.description(), entry.command(), entry.args());
                provider.start();
                yield provider;
            }
            case "sse" -> {
                if (entry.url() == null || entry.url().isBlank()) {
                    throw new IllegalArgumentException("MCP server '" + name + "' (sse) requires a url");
                }
                SseMcpToolProvider provider = new SseMcpToolProvider(
                        name, entry.description(), entry.url());
                provider.connect();
                yield provider;
            }
            case "http" -> {
                if (entry.url() == null || entry.url().isBlank()) {
                    throw new IllegalArgumentException("MCP server '" + name + "' (http) requires a url");
                }
                HttpMcpToolProvider provider = new HttpMcpToolProvider(
                        name, entry.description(), entry.url(), entry.authToken());
                provider.initialize();
                yield provider;
            }
            default -> throw new IllegalArgumentException(
                    "MCP server '" + name + "' has unknown transport type: " + type);
        };
    }
}

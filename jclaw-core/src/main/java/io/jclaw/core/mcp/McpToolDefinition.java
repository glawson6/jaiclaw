package io.jclaw.core.mcp;

/**
 * Definition of a tool exposed via a hosted MCP server.
 *
 * @param name        tool name (e.g. "resource_search")
 * @param description human-readable description for the MCP client
 * @param inputSchema JSON Schema describing the tool's parameters
 */
public record McpToolDefinition(
        String name,
        String description,
        String inputSchema
) {}

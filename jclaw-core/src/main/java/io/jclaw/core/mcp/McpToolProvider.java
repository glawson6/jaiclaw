package io.jclaw.core.mcp;

import io.jclaw.core.tenant.TenantContext;

import java.util.List;
import java.util.Map;

/**
 * SPI for contributing tools to a hosted MCP server.
 * Modules implement this interface to expose tools that external MCP clients
 * (or the JClaw agent itself) can invoke.
 *
 * <p>Each provider is associated with a named MCP server (e.g. "resources",
 * "compliance"). The gateway hosts each server at {@code /mcp/{serverName}}.
 */
public interface McpToolProvider {

    /** The MCP server name this provider contributes to. */
    String getServerName();

    /** Description of the MCP server for discovery. */
    String getServerDescription();

    /** List of tool definitions this provider exposes. */
    List<McpToolDefinition> getTools();

    /**
     * Execute a tool by name with the given arguments.
     *
     * @param toolName the tool to execute
     * @param args     tool parameters
     * @param tenant   current tenant context (may be null in single-tenant mode)
     * @return the tool execution result
     */
    McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant);
}

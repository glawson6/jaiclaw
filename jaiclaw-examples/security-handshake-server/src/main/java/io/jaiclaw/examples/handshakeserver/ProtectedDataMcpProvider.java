package io.jaiclaw.examples.handshakeserver;

import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Simple MCP tool provider returning secret data.
 * Token validation is handled by {@link McpToolController} — this provider
 * only needs to return the protected content.
 */
public class ProtectedDataMcpProvider implements McpToolProvider {

    @Override
    public String getServerName() {
        return "data";
    }

    @Override
    public String getServerDescription() {
        return "Protected data tools — requires a valid session token from the security handshake";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("get_secret_data",
                        "Returns secret data only accessible after completing the security handshake",
                        """
                        {"type": "object", "properties": {}}""")
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if ("get_secret_data".equals(toolName)) {
            return McpToolResult.success("""
                    {"status": "ACCESS GRANTED", \
                    "secret": "The quantum key is 42.", \
                    "timestamp": "%s"}""".formatted(Instant.now()));
        }
        return McpToolResult.error("Unknown tool: " + toolName);
    }
}

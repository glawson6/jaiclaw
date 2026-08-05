package io.jaiclaw.tools.github.mcp;

import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes the JaiClaw GitHub tools over a hosted MCP server at
 * {@code /mcp/github}. Delegates to the same {@link ToolCallback} beans
 * the in-process agent uses — same code path, same schemas.
 */
public class GithubToolsMcpProvider implements McpToolProvider {

    private static final String SERVER_NAME = "github";

    private final Map<String, ToolCallback> toolsByName;

    public GithubToolsMcpProvider(List<ToolCallback> githubTools) {
        this.toolsByName = githubTools.stream()
                .collect(Collectors.toMap(t -> t.definition().name(), t -> t));
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "GitHub tools — issue and PR metadata, thread/review/commit comments, diffs, "
                + "changed files, commit history, file contents. Backed by the Kohsuke github-api client.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return toolsByName.values().stream()
                .map(t -> new McpToolDefinition(
                        t.definition().name(),
                        t.definition().description(),
                        t.definition().inputSchema()))
                .collect(Collectors.toList());
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        ToolCallback tool = toolsByName.get(toolName);
        if (tool == null) {
            return McpToolResult.error("Unknown tool: " + toolName);
        }
        ToolResult result = tool.execute(args, null);
        if (result instanceof ToolResult.Success success) {
            return McpToolResult.success(success.content());
        }
        if (result instanceof ToolResult.Error error) {
            return McpToolResult.error(error.message());
        }
        return McpToolResult.error("Unexpected result type: " + result);
    }
}

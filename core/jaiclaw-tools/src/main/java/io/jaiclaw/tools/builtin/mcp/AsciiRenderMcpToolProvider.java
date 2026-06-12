package io.jaiclaw.tools.builtin.mcp;

import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AsciiBoxTool;
import io.jaiclaw.tools.builtin.AsciiRenderTool;

import java.util.List;
import java.util.Map;

/**
 * Exposes the {@link AsciiRenderTool} + {@link AsciiBoxTool} pair over
 * a hosted MCP server at {@code /mcp/ascii-render} so external clients
 * (other LLMs, Claude Desktop, downstream services) can call the
 * renderer remotely.
 *
 * <p>Both code paths — the in-process built-in tools and this MCP
 * surface — call the same library classes. The wire format and schemas
 * are identical to the built-in equivalents.
 */
public class AsciiRenderMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "ascii-render";

    private final AsciiRenderTool renderTool;
    private final AsciiBoxTool boxTool;

    public AsciiRenderMcpToolProvider() {
        this(new AsciiRenderTool(), new AsciiBoxTool());
    }

    public AsciiRenderMcpToolProvider(AsciiRenderTool renderTool, AsciiBoxTool boxTool) {
        this.renderTool = renderTool;
        this.boxTool = boxTool;
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "ASCII art rendering — diagrams, boxes, plots, tables. "
                + "Composable scene tool plus a quick text-in-a-box shortcut.";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition(
                        renderTool.definition().name(),
                        renderTool.definition().description(),
                        renderTool.definition().inputSchema()),
                new McpToolDefinition(
                        boxTool.definition().name(),
                        boxTool.definition().description(),
                        boxTool.definition().inputSchema())
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        ToolResult result;
        if (renderTool.definition().name().equals(toolName)) {
            result = renderTool.execute(args, null);
        } else if (boxTool.definition().name().equals(toolName)) {
            result = boxTool.execute(args, null);
        } else {
            return McpToolResult.error("Unknown tool: " + toolName);
        }
        if (result instanceof ToolResult.Success success) {
            return McpToolResult.success(success.content());
        }
        if (result instanceof ToolResult.Error error) {
            return McpToolResult.error(error.message());
        }
        return McpToolResult.error("Unexpected result type: " + result);
    }
}

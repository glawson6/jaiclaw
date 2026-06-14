package io.jaiclaw.agentmind.memory.mcp;

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties;
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.agent.StaleMemoryVersionException;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.tenant.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for operator-only tenant Memory writes. Server name:
 * {@code agentmind-memory-tenant-admin}.
 *
 * <p>One tool: {@code memory_tenant_write}. Exposed on a separate server
 * name from {@link MemoryMcpToolProvider} so MCP host configuration can
 * restrict it to admin clients independently of the read surface — admin
 * MCP clients connect to {@code agentmind-memory-tenant-admin}; general
 * clients connect only to {@code agentmind-memory}.
 *
 * <p>Plan §6 task 2.9.
 */
public class TenantMemoryMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "agentmind-memory-tenant-admin";
    private static final String SERVER_DESCRIPTION =
            "AgentMind Memory tenant-scope write — operator-only, restrict to admin MCP clients";

    private static final String WRITE_SCHEMA = """
            {"type":"object","properties":{
              "content":{"type":"string","description":"Full content of the tenant Memory document (REPLACES current)."}
            },"required":["content"]}""";

    private final AgentMindMemoryProvider memoryProvider;
    private final MemoryOverflowPolicy overflowPolicy;
    private final AgentMindMemoryProperties props;

    public TenantMemoryMcpToolProvider(AgentMindMemoryProvider memoryProvider,
                                       MemoryOverflowPolicy overflowPolicy,
                                       AgentMindMemoryProperties props) {
        this.memoryProvider = memoryProvider;
        this.overflowPolicy = overflowPolicy;
        this.props = props;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("memory_tenant_write",
                        "Replace the TENANT-scope Memory content for the current tenant. "
                                + "Operator-only — host this server behind an admin-restricted MCP endpoint.",
                        WRITE_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (!"memory_tenant_write".equals(toolName)) {
            return McpToolResult.error("Unknown tool: " + toolName);
        }
        if (args == null) return McpToolResult.error("Missing arguments");
        Object content = args.get("content");
        if (!(content instanceof String text)) {
            return McpToolResult.error("Missing required parameter: content");
        }
        String tenantId = tenant == null ? "default" : tenant.getTenantId();
        int budget = props.budgets().tenantChars();
        try {
            Optional<MemoryDocument> existing = memoryProvider.findMemory(
                    tenantId, MemoryScope.TENANT, null, null);
            long nextVersion = existing.map(d -> d.version() + 1L).orElse(0L);
            MemoryDocument toWrite = new MemoryDocument(MemoryScope.TENANT, tenantId, null, null,
                    text, budget, Instant.now(), nextVersion);

            if (toWrite.content().length() > budget) {
                toWrite = overflowPolicy.resolve(toWrite);
            }
            MemoryDocument saved = memoryProvider.saveMemory(toWrite);
            return McpToolResult.success("Tenant Memory updated to v" + saved.version()
                    + " (" + saved.content().length() + "/" + budget + " chars)");
        } catch (MemoryOverflowException e) {
            return McpToolResult.error("Overflow: " + e.getMessage());
        } catch (StaleMemoryVersionException e) {
            return McpToolResult.error("Conflict: " + e.getMessage());
        }
    }
}

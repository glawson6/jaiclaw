package io.jaiclaw.hermes.soul.mcp;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.agent.StaleSoulVersionException;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for operator-only tenant Soul writes. Server name:
 * {@code hermes-soul-tenant-admin}.
 *
 * <p>One tool: {@code soul_tenant_write}. The tool is exposed on a separate
 * server name from {@link SoulMcpToolProvider} so MCP host configuration can
 * restrict it to admin clients independently of the read surface — admin
 * MCP clients connect to {@code hermes-soul-tenant-admin}; general clients
 * connect only to {@code hermes-soul}.
 *
 * <p>Plan §5 task 1.16.
 */
public class TenantSoulMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "hermes-soul-tenant-admin";
    private static final String SERVER_DESCRIPTION =
            "Hermes Soul tenant-scope write — operator-only, restrict to admin MCP clients";

    private static final String WRITE_SCHEMA = """
            {"type":"object","properties":{
              "markdown":{"type":"string","description":"Full markdown body of the tenant Soul (REPLACES current)."}
            },"required":["markdown"]}""";

    private final SoulProvider soulProvider;

    public TenantSoulMcpToolProvider(SoulProvider soulProvider) {
        this.soulProvider = soulProvider;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("soul_tenant_write",
                        "Replace the TENANT-scope Soul markdown for the current tenant. "
                                + "Operator-only — host this server behind an admin-restricted MCP endpoint.",
                        WRITE_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (!"soul_tenant_write".equals(toolName)) {
            return McpToolResult.error("Unknown tool: " + toolName);
        }
        if (args == null) return McpToolResult.error("Missing arguments");
        Object md = args.get("markdown");
        if (!(md instanceof String markdown)) {
            return McpToolResult.error("Missing required parameter: markdown");
        }
        String tenantId = tenant == null ? "default" : tenant.getTenantId();
        try {
            Optional<Soul> existing = soulProvider.findSoul(tenantId, SoulScope.TENANT, null);
            long nextVersion = existing.map(s -> s.version() + 1L).orElse(0L);
            Soul toWrite = new Soul(SoulScope.TENANT, tenantId, null,
                    markdown, Instant.now(), nextVersion);
            Soul saved = soulProvider.saveSoul(toWrite);
            return McpToolResult.success("Tenant Soul updated to v" + saved.version());
        } catch (StaleSoulVersionException e) {
            return McpToolResult.error("Conflict: " + e.getMessage());
        }
    }
}

package io.jaiclaw.hermes.soul.mcp;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for Soul reads. Server name: {@code hermes-soul}.
 * Two tools:
 *
 * <ul>
 *   <li>{@code soul_read} — returns the current TENANT or AGENT Soul markdown
 *       for the current tenant. Read-only.</li>
 *   <li>{@code soul_reflect} — returns a concise summary of which Soul scopes
 *       are populated (TENANT? AGENT?) and their versions. Useful for
 *       operator dashboards.</li>
 * </ul>
 *
 * <p>Writes are NOT exposed here — see {@link TenantSoulMcpToolProvider} for
 * the operator-only write tool and {@code SoulAgentTool} for the per-agent
 * authoring path. Plan §5 task 1.9.
 */
public class SoulMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "hermes-soul";
    private static final String SERVER_DESCRIPTION =
            "Hermes Soul read access — markdown personality overlay spliced into the system prompt";

    private static final String READ_SCHEMA = """
            {"type":"object","properties":{
              "scope":{"type":"string","enum":["TENANT","AGENT"],
                       "description":"Which Soul scope to read."},
              "agentId":{"type":"string","description":"Required when scope=AGENT."}
            },"required":["scope"]}""";

    private static final String REFLECT_SCHEMA = """
            {"type":"object","properties":{
              "agentId":{"type":"string",
                         "description":"Optional — when provided, the reflection also reports the AGENT-scope Soul's presence and version."}
            }}""";

    private final SoulProvider soulProvider;

    public SoulMcpToolProvider(SoulProvider soulProvider) {
        this.soulProvider = soulProvider;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("soul_read",
                        "Read the markdown body of the requested Soul scope for the current tenant.",
                        READ_SCHEMA),
                new McpToolDefinition("soul_reflect",
                        "Report which Soul scopes are populated (TENANT, AGENT) and their versions.",
                        REFLECT_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        String tenantId = tenantIdOf(tenant);
        return switch (toolName) {
            case "soul_read" -> handleRead(args, tenantId);
            case "soul_reflect" -> handleReflect(args, tenantId);
            default -> McpToolResult.error("Unknown tool: " + toolName);
        };
    }

    private McpToolResult handleRead(Map<String, Object> args, String tenantId) {
        String scopeStr = stringArg(args, "scope");
        if (scopeStr == null || scopeStr.isBlank()) {
            return McpToolResult.error("Missing required parameter: scope");
        }
        SoulScope scope;
        try {
            scope = SoulScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Unknown scope: " + scopeStr);
        }
        String agentId = stringArg(args, "agentId");
        if (scope == SoulScope.AGENT && (agentId == null || agentId.isBlank())) {
            return McpToolResult.error("scope=AGENT requires agentId");
        }
        Optional<Soul> soul = soulProvider.findSoul(tenantId, scope, scope == SoulScope.AGENT ? agentId : null);
        return soul.map(s -> McpToolResult.success(s.markdown()))
                .orElseGet(() -> McpToolResult.error("No Soul found for scope=" + scope
                        + (agentId != null ? ", agentId=" + agentId : "")));
    }

    private McpToolResult handleReflect(Map<String, Object> args, String tenantId) {
        String agentId = stringArg(args, "agentId");
        StringBuilder sb = new StringBuilder();
        Optional<Soul> tenantSoul = soulProvider.findSoul(tenantId, SoulScope.TENANT, null);
        sb.append("tenant: ");
        tenantSoul.ifPresentOrElse(
                s -> sb.append("v").append(s.version()).append(" (")
                        .append(s.markdown().length()).append(" chars)"),
                () -> sb.append("absent"));
        if (agentId != null && !agentId.isBlank()) {
            Optional<Soul> agentSoul = soulProvider.findSoul(tenantId, SoulScope.AGENT, agentId);
            sb.append("\nagent[").append(agentId).append("]: ");
            agentSoul.ifPresentOrElse(
                    s -> sb.append("v").append(s.version()).append(" (")
                            .append(s.markdown().length()).append(" chars)"),
                    () -> sb.append("absent"));
        }
        return McpToolResult.success(sb.toString());
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    private static String tenantIdOf(TenantContext tenant) {
        return tenant == null ? "default" : tenant.getTenantId();
    }
}

package io.jaiclaw.agentmind.tendencies.mcp;

import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for Tendencies read access. Server name:
 * {@code agentmind-tendencies}.
 *
 * <ul>
 *   <li>{@code tendencies_observe} — return the current peerCard markdown
 *       + structured trait map for a (tenant, userKey)</li>
 *   <li>{@code tendencies_query} — convenience tool that returns a
 *       specific trait value (or "absent" when not present)</li>
 * </ul>
 *
 * <p>No write tool — Tendencies are computed by the dialectic pipeline,
 * not authored directly. Plan §8 task 3.11.
 */
public class TendenciesMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "agentmind-tendencies";
    private static final String SERVER_DESCRIPTION =
            "AgentMind Tendencies read access — per-user learned representation";

    private static final String OBSERVE_SCHEMA = """
            {"type":"object","properties":{
              "userKey":{"type":"string","description":"per-user key (canonical id or hash)"}
            },"required":["userKey"]}""";

    private static final String QUERY_SCHEMA = """
            {"type":"object","properties":{
              "userKey":{"type":"string","description":"per-user key"},
              "traitKey":{"type":"string","description":"trait to look up (e.g. prefers_brevity)"}
            },"required":["userKey","traitKey"]}""";

    private final TendenciesStoreProvider store;

    public TendenciesMcpToolProvider(TendenciesStoreProvider store) {
        this.store = store;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("tendencies_observe",
                        "Return the current PeerCard markdown + structured trait map "
                                + "for the (tenant, userKey) pair.",
                        OBSERVE_SCHEMA),
                new McpToolDefinition("tendencies_query",
                        "Return the value of a specific trait, or 'absent' when not present.",
                        QUERY_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        String tenantId = tenantIdOf(tenant);
        return switch (toolName) {
            case "tendencies_observe" -> handleObserve(args, tenantId);
            case "tendencies_query" -> handleQuery(args, tenantId);
            default -> McpToolResult.error("Unknown tool: " + toolName);
        };
    }

    private McpToolResult handleObserve(Map<String, Object> args, String tenantId) {
        String userKey = stringArg(args, "userKey");
        if (userKey == null || userKey.isBlank()) {
            return McpToolResult.error("Missing required parameter: userKey");
        }
        Optional<Tendencies> doc = store.findTendencies(tenantId, TendenciesScope.USER, userKey);
        if (doc.isEmpty()) {
            return McpToolResult.error("No Tendencies found for userKey=" + userKey);
        }
        Tendencies t = doc.get();
        StringBuilder sb = new StringBuilder();
        sb.append("version: ").append(t.version()).append('\n');
        sb.append("dialecticPasses: ").append(t.dialecticPasses()).append('\n');
        sb.append("traits: ").append(t.traits()).append('\n');
        sb.append("---\n");
        sb.append(t.peerCardMarkdown());
        return McpToolResult.success(sb.toString());
    }

    private McpToolResult handleQuery(Map<String, Object> args, String tenantId) {
        String userKey = stringArg(args, "userKey");
        String traitKey = stringArg(args, "traitKey");
        if (userKey == null || userKey.isBlank() || traitKey == null || traitKey.isBlank()) {
            return McpToolResult.error("Missing required parameters: userKey, traitKey");
        }
        Optional<Tendencies> doc = store.findTendencies(tenantId, TendenciesScope.USER, userKey);
        if (doc.isEmpty()) {
            return McpToolResult.success("absent");
        }
        String value = doc.get().traits().getOrDefault(traitKey, "absent");
        return McpToolResult.success(value);
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

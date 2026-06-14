package io.jaiclaw.agentmind.memory.mcp;

import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP tool provider for Memory reads. Server name: {@code agentmind-memory}.
 *
 * <ul>
 *   <li>{@code memory_read} — returns the current TENANT, AGENT, or PEER
 *       Memory content for the current tenant. Read-only.</li>
 *   <li>{@code memory_reflect} — concise summary of which Memory scopes are
 *       populated and their versions + sizes. Useful for operator
 *       dashboards.</li>
 * </ul>
 *
 * <p>Writes are NOT exposed here — {@link TenantMemoryMcpToolProvider}
 * carries the operator-only write tool. Plan §6 task 2.9.
 */
public class MemoryMcpToolProvider implements McpToolProvider {

    private static final String SERVER_NAME = "agentmind-memory";
    private static final String SERVER_DESCRIPTION =
            "AgentMind Memory read access — bounded markdown blobs spliced into the system prompt";

    private static final String READ_SCHEMA = """
            {"type":"object","properties":{
              "scope":{"type":"string","enum":["TENANT","AGENT","PEER"],
                       "description":"Which Memory scope to read."},
              "agentId":{"type":"string","description":"Required when scope=AGENT or scope=PEER."},
              "peerId":{"type":"string","description":"Required when scope=PEER."}
            },"required":["scope"]}""";

    private static final String REFLECT_SCHEMA = """
            {"type":"object","properties":{
              "agentId":{"type":"string","description":"Optional — when provided, the reflection includes AGENT scope (and PEER if peerId is also given)."},
              "peerId":{"type":"string","description":"Optional — provide together with agentId for PEER reflection."}
            }}""";

    private final AgentMindMemoryProvider memoryProvider;

    public MemoryMcpToolProvider(AgentMindMemoryProvider memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("memory_read",
                        "Read the content of the requested Memory scope for the current tenant.",
                        READ_SCHEMA),
                new McpToolDefinition("memory_reflect",
                        "Report which Memory scopes are populated for the current tenant and their versions + sizes.",
                        REFLECT_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        String tenantId = tenantIdOf(tenant);
        return switch (toolName) {
            case "memory_read" -> handleRead(args, tenantId);
            case "memory_reflect" -> handleReflect(args, tenantId);
            default -> McpToolResult.error("Unknown tool: " + toolName);
        };
    }

    private McpToolResult handleRead(Map<String, Object> args, String tenantId) {
        String scopeStr = stringArg(args, "scope");
        if (scopeStr == null || scopeStr.isBlank()) {
            return McpToolResult.error("Missing required parameter: scope");
        }
        MemoryScope scope;
        try {
            scope = MemoryScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Unknown scope: " + scopeStr);
        }
        String agentId = stringArg(args, "agentId");
        String peerId = stringArg(args, "peerId");

        if (scope == MemoryScope.AGENT && (agentId == null || agentId.isBlank())) {
            return McpToolResult.error("scope=AGENT requires agentId");
        }
        if (scope == MemoryScope.PEER) {
            if (agentId == null || agentId.isBlank()) {
                return McpToolResult.error("scope=PEER requires agentId");
            }
            if (peerId == null || peerId.isBlank()) {
                return McpToolResult.error("scope=PEER requires peerId");
            }
        }

        String docAgentId = scope == MemoryScope.TENANT ? null : agentId;
        String docPeerId = scope == MemoryScope.PEER ? peerId : null;
        Optional<MemoryDocument> doc = memoryProvider.findMemory(tenantId, scope, docAgentId, docPeerId);
        return doc.map(d -> McpToolResult.success(d.content()))
                .orElseGet(() -> McpToolResult.error("No Memory found for scope=" + scope
                        + (agentId != null ? ", agentId=" + agentId : "")
                        + (peerId != null ? ", peerId=" + peerId : "")));
    }

    private McpToolResult handleReflect(Map<String, Object> args, String tenantId) {
        String agentId = stringArg(args, "agentId");
        String peerId = stringArg(args, "peerId");
        StringBuilder sb = new StringBuilder();

        appendReflection(sb, "tenant",
                memoryProvider.findMemory(tenantId, MemoryScope.TENANT, null, null));

        if (agentId != null && !agentId.isBlank()) {
            appendReflection(sb, "agent[" + agentId + "]",
                    memoryProvider.findMemory(tenantId, MemoryScope.AGENT, agentId, null));
            if (peerId != null && !peerId.isBlank()) {
                appendReflection(sb, "peer[" + agentId + "/" + peerId + "]",
                        memoryProvider.findMemory(tenantId, MemoryScope.PEER, agentId, peerId));
            }
        }
        return McpToolResult.success(sb.toString().trim());
    }

    private static void appendReflection(StringBuilder sb, String label,
                                          Optional<MemoryDocument> maybeDoc) {
        sb.append(label).append(": ");
        if (maybeDoc.isPresent()) {
            MemoryDocument d = maybeDoc.get();
            sb.append("v").append(d.version())
                    .append(" (").append(d.content().length()).append("/")
                    .append(d.charBudget()).append(" chars)");
        } else {
            sb.append("absent");
        }
        sb.append('\n');
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

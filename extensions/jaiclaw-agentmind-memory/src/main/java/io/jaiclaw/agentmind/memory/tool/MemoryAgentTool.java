package io.jaiclaw.agentmind.memory.tool;

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties;
import io.jaiclaw.agentmind.memory.markdown.MarkdownSectionEditor;
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.agent.StaleMemoryVersionException;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code memory}: mutates a bounded markdown Memory document by
 * named section. Three actions: {@code add} (idempotent on heading),
 * {@code replace} (errors if missing), {@code remove} (idempotent on missing).
 *
 * <p>The tool deliberately omits a {@code read} action — exposing one per
 * turn would defeat the prefix-cache invariant the session-start snapshot
 * relies on (analysis §8.1, "No read API on memory — preserve").
 *
 * <p>Scope handling:
 * <ul>
 *   <li>{@code scope=AGENT} (default) — writes the AGENT-scope Memory for
 *       {@code ctx.agentId()} inside the current tenant. Always allowed
 *       when the memory pillar is enabled.</li>
 *   <li>{@code scope=PEER} — writes the PEER-scope Memory for
 *       {@code (ctx.agentId(), peerId)} inside the current tenant.
 *       Requires the {@code peerId} parameter.</li>
 *   <li>{@code scope=TENANT} — writes the org-wide TENANT-scope Memory.
 *       <b>Disabled by default.</b> Only allowed when
 *       {@code jaiclaw.agentmind.memory.tenant.agent-write-enabled=true}.
 *       When disabled, the tool returns a typed authorization error so the
 *       LLM does not retry silently.</li>
 * </ul>
 *
 * <p>Char-budget overflow is consulted from the {@link MemoryOverflowPolicy}
 * SPI. The default {@code FailFastOverflowPolicy} raises
 * {@link MemoryOverflowException}, which the tool catches and translates to
 * {@link ToolResult.Error} so the LLM gets a structured prompt back and is
 * forced to consolidate in-turn (the upstream hermes error-as-control-flow
 * pattern).
 *
 * <p>Plan §6 task 2.5.
 */
public class MemoryAgentTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
              "action":{"type":"string","enum":["add","replace","remove"],
                        "description":"What to do with the named section."},
              "heading":{"type":"string",
                         "description":"Section heading without the leading '# '."},
              "body":{"type":"string",
                      "description":"Markdown body for the section. Required for 'add' and 'replace'."},
              "scope":{"type":"string","enum":["AGENT","PEER","TENANT"],"default":"AGENT",
                       "description":"AGENT (default) writes the per-agent Memory. PEER requires peerId. TENANT is operator-gated."},
              "peerId":{"type":"string",
                         "description":"Required when scope=PEER. Channel-specific peer id (e.g. a Slack user ID)."}
            },"required":["action","heading"]}""";

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "memory",
            "Edit a bounded persistent Memory document by named section. "
                    + "Actions: add (idempotent on heading), replace (errors if missing), "
                    + "remove (idempotent). No read action — prompt caching depends on it. "
                    + "Overflow returns an error so you must consolidate in-turn.",
            "Memory",
            INPUT_SCHEMA);

    private static final Set<String> ALLOWED_ACTIONS = Set.of("add", "replace", "remove");

    private final AgentMindMemoryProvider memoryProvider;
    private final TenantGuard tenantGuard;
    private final MemoryOverflowPolicy overflowPolicy;
    private final AgentMindMemoryProperties props;

    public MemoryAgentTool(AgentMindMemoryProvider memoryProvider,
                           TenantGuard tenantGuard,
                           MemoryOverflowPolicy overflowPolicy,
                           AgentMindMemoryProperties props) {
        super(DEFINITION);
        this.memoryProvider = memoryProvider;
        this.tenantGuard = tenantGuard;
        this.overflowPolicy = overflowPolicy;
        this.props = props;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        String action = requireParam(parameters, "action").toLowerCase();
        if (!ALLOWED_ACTIONS.contains(action)) {
            return new ToolResult.Error("Unknown action: " + action
                    + ". Allowed: add, replace, remove.");
        }
        String heading = requireParam(parameters, "heading").strip();
        if (heading.isEmpty()) {
            return new ToolResult.Error("heading must not be blank.");
        }
        String scopeStr = optionalParam(parameters, "scope", "AGENT").toUpperCase();
        MemoryScope scope;
        try {
            scope = MemoryScope.valueOf(scopeStr);
        } catch (IllegalArgumentException e) {
            return new ToolResult.Error("Unknown scope: " + scopeStr
                    + ". Allowed: AGENT, PEER, TENANT.");
        }

        // TENANT writes require the operator-controlled opt-in.
        if (scope == MemoryScope.TENANT && !props.tenant().agentWriteEnabled()) {
            return new ToolResult.Error(
                    "Unauthorised: scope=TENANT writes from the agent tool are disabled. "
                            + "Set jaiclaw.agentmind.memory.tenant.agent-write-enabled=true to enable, "
                            + "or use the operator REST/MCP surface to author tenant Memory.");
        }

        String agentId = context.agentId();
        String tenantId = resolveTenantId();
        String peerId = optionalParam(parameters, "peerId", null);

        // Scope-specific argument checks.
        if (scope == MemoryScope.AGENT && (agentId == null || agentId.isBlank())) {
            return new ToolResult.Error("No agentId on tool context — cannot edit AGENT-scope Memory.");
        }
        if (scope == MemoryScope.PEER) {
            if (agentId == null || agentId.isBlank()) {
                return new ToolResult.Error("No agentId on tool context — cannot edit PEER-scope Memory.");
            }
            if (peerId == null || peerId.isBlank()) {
                return new ToolResult.Error("scope=PEER requires the 'peerId' parameter.");
            }
        }

        // Resolve which fields belong on the document for this scope.
        String docAgentId = scope == MemoryScope.TENANT ? null : agentId;
        String docPeerId = scope == MemoryScope.PEER ? peerId : null;

        try {
            Optional<MemoryDocument> existing = memoryProvider.findMemory(tenantId, scope,
                    docAgentId, docPeerId);
            String currentContent = existing.map(MemoryDocument::content).orElse("");
            long currentVersion = existing.map(MemoryDocument::version).orElse(-1L);

            String newContent = switch (action) {
                case "add" -> MarkdownSectionEditor.add(currentContent, heading,
                        requireParam(parameters, "body"));
                case "replace" -> MarkdownSectionEditor.replace(currentContent, heading,
                        requireParam(parameters, "body"));
                case "remove" -> MarkdownSectionEditor.remove(currentContent, heading);
                default -> currentContent; // unreachable, guarded above
            };

            if (newContent.equals(currentContent)) {
                return new ToolResult.Success("No change: section '" + heading
                        + "' already in the requested state.");
            }

            int budget = budgetFor(scope);
            MemoryDocument toWrite = new MemoryDocument(scope, tenantId, docAgentId, docPeerId,
                    newContent, budget, java.time.Instant.now(), currentVersion + 1);

            if (newContent.length() > budget) {
                // Overflow path: consult the policy. Default raises;
                // alternative impls (truncate/summarise) could return a
                // fitting replacement document for the store to persist.
                toWrite = overflowPolicy.resolve(toWrite);
            }

            MemoryDocument persisted = memoryProvider.saveMemory(toWrite);
            return new ToolResult.Success("Memory updated: scope=" + scope
                    + ", action=" + action
                    + ", heading='" + heading
                    + "', version=" + persisted.version()
                    + ", size=" + persisted.content().length() + "/" + budget + " chars");

        } catch (MarkdownSectionEditor.UnknownSectionException e) {
            return new ToolResult.Error(e.getMessage()
                    + ". Use action='add' to create it.");
        } catch (MemoryOverflowException e) {
            return new ToolResult.Error(
                    "Overflow: scope=" + e.scope() + " write of " + e.attemptedLength()
                            + " chars exceeds budget of " + e.charBudget() + " chars. "
                            + "Use 'replace' or 'remove' to consolidate first, then retry.");
        } catch (StaleMemoryVersionException e) {
            return new ToolResult.Error("Conflict: another writer updated the Memory. "
                    + "Re-read and retry. " + e.getMessage());
        }
    }

    private int budgetFor(MemoryScope scope) {
        return switch (scope) {
            case TENANT -> props.budgets().tenantChars();
            case AGENT -> props.budgets().agentChars();
            case PEER -> props.budgets().peerChars();
        };
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }
}

package io.jaiclaw.agentmind.soul.tool;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.agent.StaleSoulVersionException;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.agentmind.soul.markdown.MarkdownSectionEditor;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Agent tool {@code soul}: mutates the per-agent Soul markdown by named
 * section. Three actions:
 *
 * <ul>
 *   <li>{@code add} — adds or replaces a named section. Idempotent on the
 *       section heading (replacement) but bumps the Soul version on every
 *       successful write.</li>
 *   <li>{@code replace} — replaces an existing section. Returns
 *       {@code ToolResult.Error} if the section does not exist (no silent
 *       creation — matches agentmind semantics).</li>
 *   <li>{@code remove} — removes the named section. Idempotent on missing
 *       sections (no error).</li>
 * </ul>
 *
 * <p>The tool deliberately omits a {@code read} action — exposing one per
 * turn would defeat the prefix-cache invariant the
 * {@code SoulPromptInjector} preserves (plan §8.1, "No read API on
 * memory — preserve").
 *
 * <p>Writes are restricted to AGENT scope. If the LLM passes
 * {@code "scope":"TENANT"} the tool returns {@code ToolResult.Error} with an
 * authorization message — tenant Soul authoring is reserved for operators
 * via the REST + MCP write paths (plan §4.1 §1.16). Without {@code scope},
 * the tool defaults to {@code AGENT}.
 *
 * <p>Plan §5 task 1.7.
 */
public class SoulAgentTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{
              "action":{"type":"string","enum":["add","replace","remove"],
                        "description":"What to do with the named section."},
              "heading":{"type":"string",
                         "description":"Section heading without the leading '# ' (e.g. 'Identity', 'Style', 'Avoid', 'Defaults')."},
              "body":{"type":"string",
                      "description":"Markdown body for the section. Required for 'add' and 'replace'."},
              "scope":{"type":"string","enum":["AGENT"],"default":"AGENT",
                       "description":"Always AGENT for the agent tool. TENANT writes are operator-only via REST/MCP."}
            },"required":["action","heading"]}""";

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "soul",
            "Edit the agent's persistent Soul personality overlay by named section. "
                    + "Actions: add (idempotent on heading), replace (errors if missing), "
                    + "remove (idempotent). No read action — prompt caching depends on it.",
            "Soul",
            INPUT_SCHEMA);

    private static final Set<String> ALLOWED_ACTIONS = Set.of("add", "replace", "remove");

    private final SoulProvider soulProvider;
    private final TenantGuard tenantGuard;

    public SoulAgentTool(SoulProvider soulProvider, TenantGuard tenantGuard) {
        super(DEFINITION);
        this.soulProvider = soulProvider;
        this.tenantGuard = tenantGuard;
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
        if (!"AGENT".equals(scopeStr)) {
            return new ToolResult.Error(
                    "Unauthorised: scope=" + scopeStr + " is operator-only. "
                            + "The agent tool can only edit AGENT-scope Souls. "
                            + "Tenant Souls are authored via the REST/MCP admin surface.");
        }

        String agentId = context.agentId();
        if (agentId == null || agentId.isBlank()) {
            return new ToolResult.Error("No agentId on tool context — cannot edit AGENT-scope Soul.");
        }
        String tenantId = resolveTenantId();

        try {
            Optional<Soul> existing = soulProvider.findSoul(tenantId, SoulScope.AGENT, agentId);
            String currentMarkdown = existing.map(Soul::markdown).orElse("");
            long currentVersion = existing.map(Soul::version).orElse(-1L);

            String newMarkdown = switch (action) {
                case "add" -> MarkdownSectionEditor.add(currentMarkdown, heading,
                        requireParam(parameters, "body"));
                case "replace" -> MarkdownSectionEditor.replace(currentMarkdown, heading,
                        requireParam(parameters, "body"));
                case "remove" -> MarkdownSectionEditor.remove(currentMarkdown, heading);
                default -> currentMarkdown; // unreachable, guarded above
            };

            if (newMarkdown.equals(currentMarkdown)) {
                return new ToolResult.Success("No change: section '" + heading
                        + "' already in the requested state.");
            }

            long nextVersion = currentVersion + 1;
            Soul toWrite = new Soul(SoulScope.AGENT, tenantId, agentId,
                    newMarkdown, java.time.Instant.now(), nextVersion);
            soulProvider.saveSoul(toWrite);
            return new ToolResult.Success("Soul updated: action=" + action
                    + ", heading='" + heading + "', version=" + nextVersion);
        } catch (MarkdownSectionEditor.UnknownSectionException e) {
            return new ToolResult.Error(e.getMessage()
                    + ". Use action='add' to create it.");
        } catch (StaleSoulVersionException e) {
            return new ToolResult.Error("Conflict: another writer updated the Soul. "
                    + "Re-read and retry. " + e.getMessage());
        }
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }
}

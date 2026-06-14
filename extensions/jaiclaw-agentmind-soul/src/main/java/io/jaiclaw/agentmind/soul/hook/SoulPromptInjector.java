package io.jaiclaw.agentmind.soul.hook;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.agentmind.soul.AgentMindSoulProperties;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Modifies the system prompt at {@link BeforePromptBuildEvent} time, splicing
 * Soul markdown verbatim immediately after the identity line built by
 * {@code SystemPromptBuilder}.
 *
 * <p>Layering order (most-general → most-specific):
 * <ol>
 *   <li>TENANT-scope Soul (when {@code jaiclaw.agentmind.soul.tenant.enabled=true}
 *       and a tenant Soul exists for the current tenant)</li>
 *   <li>AGENT-scope Soul for {@code event.agentId()}</li>
 * </ol>
 *
 * <p>Empty / absent records are omitted entirely — no placeholder headers,
 * no zero-length fences — to preserve prefix-cache stability when an operator
 * later introduces an overlay.
 *
 * <p>Plan §5 tasks 1.6 + 1.17.
 */
public class SoulPromptInjector implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(SoulPromptInjector.class);

    private final SoulProvider soulProvider;
    private final TenantGuard tenantGuard;
    private final AgentMindSoulProperties props;

    public SoulPromptInjector(SoulProvider soulProvider, TenantGuard tenantGuard,
                              AgentMindSoulProperties props) {
        this.soulProvider = soulProvider;
        this.tenantGuard = tenantGuard;
        this.props = props;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("agentmind-soul-prompt-injector")
                .name("AgentMind Soul Prompt Injector")
                .description("Splices Soul markdown into the system prompt at BeforePromptBuildEvent. "
                        + "Layers TENANT scope before AGENT scope; empty scopes are omitted.")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        api.on(BeforePromptBuildEvent.class, this::rewrite);
    }

    BeforePromptBuildEvent rewrite(BeforePromptBuildEvent event) {
        String tenantId = resolveTenantId();
        String injected = injectSoulSections(event.systemPrompt(), tenantId, event.agentId());
        if (injected.equals(event.systemPrompt())) {
            return null; // unchanged — let the runner short-circuit
        }
        return event.withSystemPrompt(injected);
    }

    String injectSoulSections(String originalPrompt, String tenantId, String agentId) {
        if (tenantId == null || tenantId.isBlank() || agentId == null || agentId.isBlank()) {
            // No tenant or agent context — cannot key the Soul lookup safely.
            return originalPrompt;
        }
        String tenantMarkdown = props.tenant().enabled() ? loadMarkdown(tenantId, SoulScope.TENANT, null) : "";
        String agentMarkdown = loadMarkdown(tenantId, SoulScope.AGENT, agentId);

        if (tenantMarkdown.isBlank() && agentMarkdown.isBlank()) {
            return originalPrompt;
        }

        StringBuilder out = new StringBuilder();
        int insertAt = identityBlockEnd(originalPrompt);
        out.append(originalPrompt, 0, insertAt);

        if (!tenantMarkdown.isBlank()) {
            out.append(tenantMarkdown.stripTrailing()).append("\n\n");
        }
        if (!agentMarkdown.isBlank()) {
            out.append(agentMarkdown.stripTrailing()).append("\n\n");
        }
        out.append(originalPrompt.substring(insertAt));
        return out.toString();
    }

    private String loadMarkdown(String tenantId, SoulScope scope, String agentId) {
        try {
            Optional<Soul> soul = soulProvider.findSoul(tenantId, scope, agentId);
            return soul.map(Soul::markdown).orElse("");
        } catch (Exception e) {
            log.warn("Failed to load Soul (scope={}, tenant={}, agent={}): {}",
                    scope, tenantId, agentId, e.getMessage());
            return "";
        }
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }

    /**
     * The identity block built by {@code SystemPromptBuilder} starts at offset 0
     * with {@code "You are …\n"} and ends at the first blank line. Soul sections
     * are spliced immediately after that boundary so they precede the
     * "Respond directly to the user" behaviour preamble.
     *
     * <p>Falls back to inserting at offset 0 if the original prompt does not
     * contain a blank line (defensive — should never happen with the canonical
     * builder).
     */
    static int identityBlockEnd(String prompt) {
        int firstBlank = prompt.indexOf("\n\n");
        if (firstBlank < 0) return 0;
        return firstBlank + 2;
    }
}

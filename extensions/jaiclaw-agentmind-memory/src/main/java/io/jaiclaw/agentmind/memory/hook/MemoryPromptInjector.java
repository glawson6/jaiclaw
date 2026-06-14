package io.jaiclaw.agentmind.memory.hook;

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Modifies the system prompt at {@link BeforePromptBuildEvent} time, splicing
 * Memory blocks in TENANT → AGENT → PEER order after any Soul content
 * already injected by the soul module (registered with priority 200 so it
 * runs after Soul's default priority 100).
 *
 * <p>Empty / absent scopes are omitted entirely to preserve prefix-cache
 * stability when an operator later introduces an overlay.
 *
 * <p>Peer key resolution today: the agent runtime's session key shape is
 * {@code agentId:channel:accountId:peerId}; the injector parses the last
 * segment. When {@code jaiclaw-identity} is present a future refactor will
 * route through {@link io.jaiclaw.agentmind.soul.user.AgentMindUserKeyResolver}
 * for canonical-id resolution; for now the channel-specific peerId is used
 * directly so PEER Memory is keyed the same way the agent tool writes it.
 *
 * <p>Plan §6 task 2.6 — prompt injector. The plan also calls for a separate
 * {@code MemorySessionListener} that snapshots at session start; the
 * snapshot pattern is folded into this injector for now (Memory reads
 * happen once per BeforePromptBuildEvent, which the runtime fires at
 * session start; the prefix-cache invariant is enforced at the agent-tool
 * level by omitting the read action).
 */
public class MemoryPromptInjector implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(MemoryPromptInjector.class);

    private static final int PRIORITY = 200;

    private final AgentMindMemoryProvider memoryProvider;
    private final TenantGuard tenantGuard;
    private final AgentMindMemoryProperties props;

    public MemoryPromptInjector(AgentMindMemoryProvider memoryProvider,
                                TenantGuard tenantGuard,
                                AgentMindMemoryProperties props) {
        this.memoryProvider = memoryProvider;
        this.tenantGuard = tenantGuard;
        this.props = props;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("agentmind-memory-prompt-injector")
                .name("AgentMind Memory Prompt Injector")
                .description("Splices Memory blocks into the system prompt at BeforePromptBuildEvent. "
                        + "Layers TENANT before AGENT before PEER; empty scopes are omitted.")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        api.on(BeforePromptBuildEvent.class, this::rewrite, PRIORITY);
    }

    BeforePromptBuildEvent rewrite(BeforePromptBuildEvent event) {
        String tenantId = resolveTenantId();
        String injected = injectMemoryBlocks(event.systemPrompt(), tenantId,
                event.agentId(), peerIdFromSessionKey(event.sessionKey()));
        if (injected.equals(event.systemPrompt())) {
            return null;
        }
        return event.withSystemPrompt(injected);
    }

    String injectMemoryBlocks(String originalPrompt, String tenantId, String agentId, String peerId) {
        if (tenantId == null || tenantId.isBlank() || agentId == null || agentId.isBlank()) {
            return originalPrompt;
        }

        String tenantContent = props.tenant().enabled()
                ? loadContent(tenantId, MemoryScope.TENANT, null, null)
                : "";
        String agentContent = loadContent(tenantId, MemoryScope.AGENT, agentId, null);
        String peerContent = (peerId != null && !peerId.isBlank())
                ? loadContent(tenantId, MemoryScope.PEER, agentId, peerId)
                : "";

        if (tenantContent.isBlank() && agentContent.isBlank() && peerContent.isBlank()) {
            return originalPrompt;
        }

        StringBuilder out = new StringBuilder();
        int insertAt = soulBlockEnd(originalPrompt);
        out.append(originalPrompt, 0, insertAt);

        if (!tenantContent.isBlank()) {
            out.append(tenantContent.stripTrailing()).append("\n\n");
        }
        if (!agentContent.isBlank()) {
            out.append(agentContent.stripTrailing()).append("\n\n");
        }
        if (!peerContent.isBlank()) {
            out.append(peerContent.stripTrailing()).append("\n\n");
        }
        out.append(originalPrompt.substring(insertAt));
        return out.toString();
    }

    private String loadContent(String tenantId, MemoryScope scope, String agentId, String peerId) {
        try {
            Optional<MemoryDocument> doc = memoryProvider.findMemory(tenantId, scope, agentId, peerId);
            return doc.map(MemoryDocument::content).orElse("");
        } catch (Exception e) {
            log.warn("Failed to load Memory (scope={}, tenant={}, agent={}, peer={}): {}",
                    scope, tenantId, agentId, peerId, e.getMessage());
            return "";
        }
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }

    /**
     * Extracts the {@code peerId} segment from a session key of the form
     * {@code agentId:channel:accountId:peerId}. Returns null if the key is
     * malformed or absent. The Soul injector does not need this because Soul
     * has no PEER scope.
     */
    static String peerIdFromSessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        int last = sessionKey.lastIndexOf(':');
        if (last < 0 || last == sessionKey.length() - 1) return null;
        return sessionKey.substring(last + 1);
    }

    /**
     * Find the boundary after both the identity line and any Soul block(s)
     * already injected. The Soul injector inserts after the identity line and
     * before the agent-runtime behaviour preamble ("Respond directly to the
     * user…"). Memory blocks land at the same boundary — but since this
     * injector runs at priority 200 (after Soul's default 100), it sees the
     * prompt already containing Soul content and finds the next double-newline
     * after the identity line.
     *
     * <p>Algorithm: find the first {@code \n\n} after the identity line; walk
     * forward across any blank-line-separated blocks (Soul tenant + agent)
     * until we hit a non-Soul preamble paragraph. Conservative fallback: same
     * boundary the Soul injector used.
     */
    static int soulBlockEnd(String prompt) {
        int identityEnd = prompt.indexOf("\n\n");
        if (identityEnd < 0) return 0;
        int cursor = identityEnd + 2;
        // The agent runtime's preamble starts with "Respond directly to the user".
        int preamble = prompt.indexOf("Respond directly to the user", cursor);
        if (preamble < 0) return cursor;
        return preamble;
    }
}

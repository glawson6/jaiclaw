package io.jaiclaw.observability;

import io.jaiclaw.core.hook.event.AgentStartedEvent;
import io.jaiclaw.core.hook.event.MessageReceivedEvent;
import io.jaiclaw.core.hook.event.MessageSentEvent;
import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin that wires JaiClaw lifecycle hooks to Micrometer metrics.
 * Registers hook handlers for agent invocations, tool calls, and channel messages,
 * recording them via {@link JaiClawMetrics}.
 *
 * <p>0.8.0 hard-break: handlers are registered against typed
 * {@link io.jaiclaw.core.hook.event.HookEvent} subclasses rather than the
 * pre-0.8.0 {@code HookName} enum, and access fields directly off the typed
 * record instead of casting from {@code Map<String, Object>}. The 18-line
 * {@code extractString} / {@code extractBoolean} pair the audit called out
 * is now gone — the LLM's type inference is the schema, not a map-key
 * convention. See {@code docs/MIGRATION-0.8.md} § P3.1.
 */
public class ObservabilityPlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityPlugin.class);

    private final JaiClawMetrics metrics;
    private final Map<String, Long> toolStartTimes = new ConcurrentHashMap<>();

    public ObservabilityPlugin(JaiClawMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("observability")
                .name("Observability")
                .description("Records Micrometer metrics for agent invocations, tool calls, and channel messages")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        // Agent lifecycle metrics
        api.on(AgentStartedEvent.class, event -> {
            // sessionKey carries the channel id as the second segment
            // ({agentId}:{channel}:{accountId}:{peerId}); the pipeline hook
            // firer passes a non-session key (just the executionId), so we
            // tolerate both shapes here.
            String channelId = extractChannelFromSessionKey(event.sessionKey());
            metrics.recordAgentInvocation(event.agentId(), channelId);
            log.debug("Recorded agent invocation: agentId={}, channelId={}", event.agentId(), channelId);
            return null;
        });

        // Tool call metrics — start
        api.on(ToolCallStartedEvent.class, event -> {
            toolStartTimes.put(event.toolName() + ":" + Thread.currentThread().threadId(),
                    System.currentTimeMillis());
            return null;
        });

        // Tool call metrics — end
        api.on(ToolCallEndedEvent.class, event -> {
            String key = event.toolName() + ":" + Thread.currentThread().threadId();
            Long startTime = toolStartTimes.remove(key);
            long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;
            metrics.recordToolCall(event.toolName(), event.success(), durationMs);
            log.debug("Recorded tool call: toolName={}, success={}, durationMs={}",
                    event.toolName(), event.success(), durationMs);
            return null;
        });

        // Channel message metrics — inbound
        api.on(MessageReceivedEvent.class, event -> {
            metrics.recordChannelMessage(event.channelId(), "inbound");
            return null;
        });

        // Channel message metrics — outbound
        api.on(MessageSentEvent.class, event -> {
            metrics.recordChannelMessage(event.channelId(), "outbound");
            return null;
        });

        log.info("Observability plugin registered — hooks wired for agent, tool, and channel metrics");
    }

    /**
     * Extract the channel id from a session key of the form
     * {@code agentId:channel:accountId:peerId}. Returns null if the key
     * doesn't carry a channel segment (e.g. pipeline execution ids).
     */
    private static String extractChannelFromSessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        int firstColon = sessionKey.indexOf(':');
        if (firstColon < 0) return null;
        int secondColon = sessionKey.indexOf(':', firstColon + 1);
        if (secondColon < 0) return null;
        return sessionKey.substring(firstColon + 1, secondColon);
    }
}

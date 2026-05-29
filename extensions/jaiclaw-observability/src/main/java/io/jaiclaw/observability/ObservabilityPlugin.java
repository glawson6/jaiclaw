package io.jaiclaw.observability;

import io.jaiclaw.core.hook.HookName;
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
        api.on(HookName.BEFORE_AGENT_START, (event, context) -> {
            String agentId = extractString(event, "agentId", "unknown");
            String channelId = extractString(event, "channelId", null);
            metrics.recordAgentInvocation(agentId, channelId);
            log.debug("Recorded agent invocation: agentId={}, channelId={}", agentId, channelId);
            return null;
        });

        // Tool call metrics
        api.on(HookName.BEFORE_TOOL_CALL, (event, context) -> {
            String toolName = extractString(event, "toolName", "unknown");
            toolStartTimes.put(toolName + ":" + Thread.currentThread().threadId(),
                    System.currentTimeMillis());
            return null;
        });

        api.on(HookName.AFTER_TOOL_CALL, (event, context) -> {
            String toolName = extractString(event, "toolName", "unknown");
            boolean success = extractBoolean(event, "success", true);
            String key = toolName + ":" + Thread.currentThread().threadId();
            Long startTime = toolStartTimes.remove(key);
            long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0;
            metrics.recordToolCall(toolName, success, durationMs);
            log.debug("Recorded tool call: toolName={}, success={}, durationMs={}",
                    toolName, success, durationMs);
            return null;
        });

        // Channel message metrics
        api.on(HookName.MESSAGE_RECEIVED, (event, context) -> {
            String channelId = extractString(event, "channelId", "unknown");
            metrics.recordChannelMessage(channelId, "inbound");
            return null;
        });

        api.on(HookName.MESSAGE_SENT, (event, context) -> {
            String channelId = extractString(event, "channelId", "unknown");
            metrics.recordChannelMessage(channelId, "outbound");
            return null;
        });

        log.info("Observability plugin registered — hooks wired for agent, tool, and channel metrics");
    }

    @SuppressWarnings("unchecked")
    private String extractString(Object event, String key, String defaultValue) {
        if (event instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? value.toString() : defaultValue;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private boolean extractBoolean(Object event, String key, boolean defaultValue) {
        if (event instanceof Map<?, ?> map) {
            Object value = map.get(key);
            if (value instanceof Boolean b) return b;
            if (value != null) return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }
}

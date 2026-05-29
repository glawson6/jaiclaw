package io.jaiclaw.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Pre-built Micrometer metrics for JaiClaw operations.
 * Injected by {@link ObservabilityAutoConfiguration} when a {@link MeterRegistry} is available.
 */
public class JaiClawMetrics {

    private final MeterRegistry registry;

    public JaiClawMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record an agent invocation.
     */
    public void recordAgentInvocation(String agentId, String channelId) {
        Counter.builder(JaiClawObservations.METRIC_AGENT_INVOCATIONS)
                .tag(JaiClawObservations.TAG_AGENT_ID, agentId)
                .tag(JaiClawObservations.TAG_CHANNEL_ID, channelId != null ? channelId : "unknown")
                .register(registry)
                .increment();
    }

    /**
     * Record a tool call with duration.
     */
    public void recordToolCall(String toolName, boolean success, long durationMs) {
        Timer.builder(JaiClawObservations.METRIC_TOOL_CALLS)
                .tag(JaiClawObservations.TAG_TOOL_NAME, toolName)
                .tag(JaiClawObservations.TAG_OUTCOME, success ? "success" : "error")
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Record a channel message (inbound or outbound).
     */
    public void recordChannelMessage(String channelId, String direction) {
        Counter.builder(JaiClawObservations.METRIC_CHANNEL_MESSAGES)
                .tag(JaiClawObservations.TAG_CHANNEL_ID, channelId)
                .tag(JaiClawObservations.TAG_DIRECTION, direction)
                .register(registry)
                .increment();
    }

    /**
     * Record token usage.
     */
    public void recordTokenUsage(String agentId, String model, String tokenType, long count) {
        Counter.builder(JaiClawObservations.METRIC_TOKEN_USAGE)
                .tag(JaiClawObservations.TAG_AGENT_ID, agentId)
                .tag(JaiClawObservations.TAG_MODEL, model != null ? model : "unknown")
                .tag(JaiClawObservations.TAG_TOKEN_TYPE, tokenType)
                .register(registry)
                .increment(count);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}

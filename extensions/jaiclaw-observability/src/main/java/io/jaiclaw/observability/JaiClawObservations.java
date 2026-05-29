package io.jaiclaw.observability;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;

/**
 * Custom Micrometer observation definitions for JaiClaw.
 * Provides instrumentation for agent invocations, tool calls,
 * channel message routing, and token usage.
 */
public final class JaiClawObservations {

    private JaiClawObservations() {}

    // --- Observation names ---

    /** Observation name for agent message processing. */
    public static final String AGENT_INVOCATION = "jaiclaw.agent.invocation";

    /** Observation name for tool execution. */
    public static final String TOOL_CALL = "jaiclaw.tool.call";

    /** Observation name for channel message routing. */
    public static final String CHANNEL_MESSAGE = "jaiclaw.channel.message";

    // --- Metric names (counters/gauges) ---

    /** Counter: total agent invocations. */
    public static final String METRIC_AGENT_INVOCATIONS = "jaiclaw.agent.invocations";

    /** Timer: tool call duration. */
    public static final String METRIC_TOOL_CALLS = "jaiclaw.tool.calls";

    /** Counter: channel messages by direction. */
    public static final String METRIC_CHANNEL_MESSAGES = "jaiclaw.channel.messages";

    /** Counter: token usage by type. */
    public static final String METRIC_TOKEN_USAGE = "jaiclaw.tokens.usage";

    /** Gauge: active sessions. */
    public static final String METRIC_SESSIONS_ACTIVE = "jaiclaw.sessions.active";

    // --- Tag keys ---

    public static final String TAG_AGENT_ID = "agentId";
    public static final String TAG_CHANNEL_ID = "channelId";
    public static final String TAG_TOOL_NAME = "toolName";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_MODEL = "model";
    public static final String TAG_TOKEN_TYPE = "tokenType";

    // --- Factory methods ---

    /**
     * Start an observation for an agent invocation.
     */
    public static Observation agentInvocation(ObservationRegistry registry,
                                               String agentId, String channelId) {
        return Observation.createNotStarted(AGENT_INVOCATION, registry)
                .lowCardinalityKeyValue(TAG_AGENT_ID, agentId)
                .lowCardinalityKeyValue(TAG_CHANNEL_ID, channelId != null ? channelId : "unknown");
    }

    /**
     * Start an observation for a tool call.
     */
    public static Observation toolCall(ObservationRegistry registry, String toolName) {
        return Observation.createNotStarted(TOOL_CALL, registry)
                .lowCardinalityKeyValue(TAG_TOOL_NAME, toolName);
    }

    /**
     * Start an observation for channel message routing.
     */
    public static Observation channelMessage(ObservationRegistry registry,
                                              String channelId, String direction) {
        return Observation.createNotStarted(CHANNEL_MESSAGE, registry)
                .lowCardinalityKeyValue(TAG_CHANNEL_ID, channelId)
                .lowCardinalityKeyValue(TAG_DIRECTION, direction);
    }
}

package io.jaiclaw.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the JaiClaw gateway. Prefix: {@code jaiclaw.gateway}.
 *
 * <p>Bound by Spring Boot's constructor binder, so this record exposes exactly
 * one public constructor per the repo rule — programmatic defaults live on
 * {@link #DEFAULT}, never on a convenience overload.
 *
 * @param autoVision   when {@code true} (default), image and PDF attachments on
 *                     inbound {@code ChannelMessage}s are auto-injected as
 *                     Spring AI {@code Media} content blocks on the user
 *                     message sent to the agent. Operators with a non-vision
 *                     chat model should set this to {@code false} (most
 *                     providers ignore unsupported media silently, but some
 *                     may hard-error). See
 *                     {@code docs/issues/attachment-injection-gap.md}.
 * @param estopMessage the reply sent to a user when the operator has engaged
 *                     the emergency stop and the gateway is refusing new work.
 *                     See {@code docs/user/EMERGENCY-STOP.md}.
 * @param openaiApiEnabled  exposes {@code POST /v1/chat/completions}. Off by
 *                     default — it is an unauthenticated-looking surface that
 *                     adopters must consciously turn on and secure.
 * @param openaiApiSessionStrategy {@code per-request} (stateless; the client's
 *                     message list is the whole history) or {@code by-user-header}
 *                     (durable session keyed on {@code X-JaiClaw-User}).
 */
@ConfigurationProperties(prefix = "jaiclaw.gateway")
public record GatewayProperties(
        @DefaultValue("true") boolean autoVision,
        @DefaultValue(DEFAULT_ESTOP_MESSAGE) String estopMessage,
        @DefaultValue("false") boolean openaiApiEnabled,
        @DefaultValue("per-request") String openaiApiSessionStrategy
) {

    /** Default refusal text while the emergency stop is engaged. */
    public static final String DEFAULT_ESTOP_MESSAGE = "Assistant is paused by the operator.";

    /** Session strategy: one throwaway session per request. */
    public static final String SESSION_PER_REQUEST = "per-request";

    /** Session strategy: durable session keyed on the {@code X-JaiClaw-User} header. */
    public static final String SESSION_BY_USER_HEADER = "by-user-header";

    public static final GatewayProperties DEFAULT = new GatewayProperties(
            true, DEFAULT_ESTOP_MESSAGE, false, SESSION_PER_REQUEST);

    public GatewayProperties {
        if (estopMessage == null || estopMessage.isBlank()) estopMessage = DEFAULT_ESTOP_MESSAGE;
        if (openaiApiSessionStrategy == null || openaiApiSessionStrategy.isBlank()) {
            openaiApiSessionStrategy = SESSION_PER_REQUEST;
        }
        openaiApiSessionStrategy = openaiApiSessionStrategy.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** True when a durable session should be derived from the user header. */
    public boolean openaiSessionByUserHeader() {
        return SESSION_BY_USER_HEADER.equals(openaiApiSessionStrategy);
    }
}

package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.time.Duration;
import java.util.Map;

/**
 * How long to wait for a human approval, and what to do when nobody answers.
 *
 * <p>{@link ApprovalFloor} says <em>whether</em> a tool needs approval;
 * this says <em>how long</em> the request stays open and what happens on
 * silence. The two are independent: a floor with no policy entry uses
 * {@link #defaultTimeout()} and {@link #defaultOnTimeout()}.
 *
 * <p>Timeouts are per tool because blast radius and human latency differ.
 * {@code shell_exec} answered in two minutes or not at all is reasonable;
 * "reply to this review" may deserve half an hour. A single global timeout
 * forces the operator to pick the wrong number for one of them.
 *
 * <h2>Auto-approve</h2>
 *
 * <p>{@link #autoApprove()} is a top-level master switch that bypasses the
 * approval gate entirely, so an operator does not have to neutralise every
 * tool individually to run unattended. Two properties hold regardless:
 *
 * <ul>
 *   <li><strong>{@link ApprovalFloor#DENY} still wins.</strong> The loop
 *       evaluates {@code DENY} before it computes whether approval is
 *       required, so auto-approve structurally cannot execute a denied tool.</li>
 *   <li><strong>It announces itself.</strong> Silently disabling a security
 *       control is how fail-open defaults survive; the runtime logs a warning
 *       when this is on.</li>
 * </ul>
 *
 * <p>This is the one sanctioned exception to the {@code ApprovalFloor}
 * contract that a floor "can only make approval stricter, never looser" — and
 * it is a deployment-wide operator decision, not something a model or a tool
 * author can set.
 *
 * @param autoApprove       when true, skip the approval gate for every tool
 *                          except those with a {@code DENY} floor
 * @param defaultTimeout    how long to wait when a tool has no explicit entry;
 *                          must be positive
 * @param defaultOnTimeout  what an unanswered request resolves to by default
 * @param tools             per-tool overrides keyed by tool name
 */
@Experimental
public record ApprovalPolicy(
        boolean autoApprove,
        Duration defaultTimeout,
        OnTimeout defaultOnTimeout,
        Map<String, ToolApprovalPolicy> tools
) {

    /**
     * What an approval request becomes when the window closes with no answer.
     *
     * <p>{@link #DENY} is the default everywhere. An unanswered request is not
     * consent, and the operator who wanted the gate is the one who is absent.
     */
    public enum OnTimeout {
        /** Resolve to a denial. The model is told the request expired. */
        DENY,
        /**
         * Resolve to an approval. Only for tools where waiting is worse than
         * acting — and never a default.
         */
        APPROVE
    }

    /**
     * Per-tool override. A null field falls back to the policy default, so an
     * operator can set just a timeout without restating the timeout action.
     *
     * @param timeout    window for this tool, or null to inherit
     * @param onTimeout  action on silence for this tool, or null to inherit
     */
    public record ToolApprovalPolicy(Duration timeout, OnTimeout onTimeout) {}

    /** Five minutes — long enough to notice a phone, short enough to not wedge a run. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /** Gate off, five-minute window, deny on silence. */
    public static final ApprovalPolicy DEFAULT =
            new ApprovalPolicy(false, DEFAULT_TIMEOUT, OnTimeout.DENY, Map.of());

    public ApprovalPolicy {
        if (defaultTimeout == null || defaultTimeout.isZero() || defaultTimeout.isNegative()) {
            defaultTimeout = DEFAULT_TIMEOUT;
        }
        if (defaultOnTimeout == null) defaultOnTimeout = OnTimeout.DENY;
        tools = tools == null ? Map.of() : Map.copyOf(tools);
    }

    /** The approval window for {@code toolName}, falling back to the default. */
    public Duration timeoutFor(String toolName) {
        ToolApprovalPolicy perTool = toolName == null ? null : tools.get(toolName);
        if (perTool == null || perTool.timeout() == null
                || perTool.timeout().isZero() || perTool.timeout().isNegative()) {
            return defaultTimeout;
        }
        return perTool.timeout();
    }

    /** The timeout action for {@code toolName}, falling back to the default. */
    public OnTimeout onTimeoutFor(String toolName) {
        ToolApprovalPolicy perTool = toolName == null ? null : tools.get(toolName);
        if (perTool == null || perTool.onTimeout() == null) {
            return defaultOnTimeout;
        }
        return perTool.onTimeout();
    }
}

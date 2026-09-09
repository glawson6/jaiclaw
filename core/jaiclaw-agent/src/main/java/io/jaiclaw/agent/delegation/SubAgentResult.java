package io.jaiclaw.agent.delegation;

import io.jaiclaw.core.api.Experimental;

/**
 * The outcome of a delegated child run.
 *
 * @param handleId       id the parent can poll or cancel with
 * @param sessionKey     the child's session key, or null when it was refused before starting
 * @param status         lifecycle state
 * @param summary        the child's answer when COMPLETED; null otherwise
 * @param iterationsUsed tool-loop iterations the child consumed
 * @param error          why the run failed or was refused; null on success
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public record SubAgentResult(
        String handleId,
        String sessionKey,
        SubAgentStatus status,
        String summary,
        int iterationsUsed,
        String error
) {
    public static SubAgentResult running(String handleId, String sessionKey) {
        return new SubAgentResult(handleId, sessionKey, SubAgentStatus.RUNNING, null, 0, null);
    }

    public static SubAgentResult completed(String handleId, String sessionKey,
                                           String summary, int iterationsUsed) {
        return new SubAgentResult(handleId, sessionKey, SubAgentStatus.COMPLETED,
                summary, iterationsUsed, null);
    }

    public static SubAgentResult failed(String handleId, String sessionKey,
                                        String error, int iterationsUsed) {
        return new SubAgentResult(handleId, sessionKey, SubAgentStatus.FAILED,
                null, iterationsUsed, error);
    }

    public static SubAgentResult cancelled(String handleId, String sessionKey, int iterationsUsed) {
        return new SubAgentResult(handleId, sessionKey, SubAgentStatus.CANCELLED,
                null, iterationsUsed, "Cancelled by the parent agent");
    }

    /** Refused before starting — depth limit, disabled, or a malformed request. */
    public static SubAgentResult refused(String reason) {
        return new SubAgentResult(null, null, SubAgentStatus.REFUSED, null, 0, reason);
    }

    public boolean isTerminal() {
        return status != SubAgentStatus.RUNNING;
    }
}

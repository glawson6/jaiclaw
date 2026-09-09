package io.jaiclaw.agent.delegation;

import io.jaiclaw.core.api.Experimental;

/**
 * Lifecycle state of a delegated child run.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public enum SubAgentStatus {
    /** Queued or executing. */
    RUNNING,
    /** Finished normally; {@code summary} carries the child's answer. */
    COMPLETED,
    /** Finished abnormally; {@code error} explains why. */
    FAILED,
    /** Cancelled by the parent before completion. */
    CANCELLED,
    /**
     * Refused before it began — depth limit exceeded, delegation disabled, or
     * an invalid request. Never a transient condition: retrying will not help.
     */
    REFUSED
}

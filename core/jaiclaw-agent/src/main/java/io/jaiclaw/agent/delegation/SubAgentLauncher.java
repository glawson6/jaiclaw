package io.jaiclaw.agent.delegation;

import io.jaiclaw.core.api.Experimental;

import java.util.Optional;

/**
 * Spawns bounded child agent runs on behalf of a parent run.
 *
 * <p>Implementations must enforce, at minimum:
 * <ul>
 *   <li><strong>Depth</strong> — refuse beyond the configured maximum. A depth
 *       violation is permanent, so it returns a REFUSED result rather than queueing.</li>
 *   <li><strong>Concurrency</strong> — bound simultaneous children per parent.
 *       Overflow <em>queues</em>: being busy is transient, and the caller's wait
 *       timeout already bounds how long it can block.</li>
 *   <li><strong>Tenant</strong> — the child runs under the parent's tenant.</li>
 *   <li><strong>Tool profile</strong> — a child may never be granted a wider
 *       profile than its parent.</li>
 * </ul>
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public interface SubAgentLauncher {

    /**
     * Starts a child run. Never throws for policy reasons — a refusal comes back
     * as a {@link SubAgentStatus#REFUSED} result so the calling tool can hand the
     * model something it can reason about.
     */
    SubAgentHandle launch(SubAgentRequest request);

    /** Looks up a live or recently-finished handle by id. */
    Optional<SubAgentHandle> find(String handleId);

    /**
     * Cancels a child by handle id.
     *
     * @return true if a handle was found and this call cancelled it
     */
    boolean cancel(String handleId);

    /** Children currently running for the given parent session. */
    int activeCount(String parentSessionKey);
}

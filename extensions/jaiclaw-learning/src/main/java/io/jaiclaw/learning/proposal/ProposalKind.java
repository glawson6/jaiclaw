package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

/**
 * What a proposal would change if applied.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public enum ProposalKind {
    /** Add or replace an entry in the agent's memory. */
    MEMORY,
    /** Create a new learned skill. */
    SKILL,
    /** Patch an existing learned skill. */
    SKILL_PATCH
}

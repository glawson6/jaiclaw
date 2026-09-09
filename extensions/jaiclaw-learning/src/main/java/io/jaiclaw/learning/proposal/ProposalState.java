package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

/**
 * Lifecycle of a learning proposal.
 *
 * <p>{@code PENDING → APPLIED | REJECTED}, and {@code APPLIED → ROLLED_BACK}.
 * Nothing leaves a terminal state except that one rollback edge — a rejected
 * proposal is not re-openable, because re-deciding an operator's rejection is
 * exactly the kind of autonomy this module is designed not to have.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public enum ProposalState {
    PENDING,
    APPLIED,
    REJECTED,
    ROLLED_BACK;

    /** Whether this state may legally transition to {@code next}. */
    public boolean canTransitionTo(ProposalState next) {
        if (next == null || next == this) return false;
        return switch (this) {
            case PENDING -> next == APPLIED || next == REJECTED;
            case APPLIED -> next == ROLLED_BACK;
            case REJECTED, ROLLED_BACK -> false;
        };
    }

    public boolean isTerminal() {
        return this == REJECTED || this == ROLLED_BACK;
    }
}

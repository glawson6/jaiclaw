package io.jaiclaw.learning.apply;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.proposal.Proposal;

/**
 * Performs the write a proposal describes, then records the state change.
 *
 * <p>Separate from {@code ProposalService} so that "did the write succeed" and
 * "is the proposal marked applied" stay distinguishable: a failed apply must
 * leave the proposal PENDING rather than claim it landed.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public interface ProposalApplier {

    /**
     * Applies a proposal if this applier handles its kind and the current mode
     * permits it.
     *
     * @param actor who requested it ({@code operator}, {@code auto}, …)
     * @return the outcome; never throws for an ordinary refusal
     */
    ApplyResult applyIfEligible(Proposal proposal, String actor);

    /** Reverses a previously applied proposal. */
    ApplyResult rollback(Proposal proposal, String actor);

    /**
     * @param applied whether the write happened
     * @param message human-readable explanation, always populated on refusal
     */
    record ApplyResult(boolean applied, String message) {
        public static ApplyResult ok(String message) {
            return new ApplyResult(true, message);
        }

        public static ApplyResult refused(String message) {
            return new ApplyResult(false, message);
        }
    }
}

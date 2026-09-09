package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.proposal.Proposal;

import java.util.List;

/**
 * What a review produced. An empty list is the normal, common result — most
 * sessions teach nothing worth keeping, and a reviewer that always finds
 * something is a reviewer producing noise.
 *
 * @param proposals what to suggest
 * @param note      optional diagnostic, surfaced in logs only
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public record ReviewOutcome(List<Proposal> proposals, String note) {

    public ReviewOutcome {
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
    }

    public static ReviewOutcome empty() {
        return new ReviewOutcome(List.of(), null);
    }

    public static ReviewOutcome of(List<Proposal> proposals) {
        return new ReviewOutcome(proposals, null);
    }

    public boolean isEmpty() {
        return proposals.isEmpty();
    }
}

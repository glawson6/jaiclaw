package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;

/**
 * Inspects a finished session and suggests what the agent could learn from it.
 *
 * <p>Implementations run on a <strong>background thread</strong> and must never
 * mutate the live session, its message list, or the runtime's system prompt.
 * That invariant is what keeps the provider's prompt cache intact — a reviewer
 * that appended to the session would invalidate the cached prefix for the next
 * turn, making every subsequent request more expensive. {@code LearningLoopE2ESpec}
 * asserts it directly.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public interface LearningReviewer {

    /**
     * Reviews a session.
     *
     * @return proposals, possibly empty; must not throw for ordinary "nothing to
     *         learn" outcomes
     */
    ReviewOutcome review(ReviewInput input);
}

package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;

import java.util.List;

/**
 * What the reviewer is given to reason about.
 *
 * @param tenantId        tenant the session belongs to
 * @param agentId         agent that ran the session
 * @param sessionKey      session under review
 * @param transcript      the conversation, already truncated to the configured budget
 * @param existingSkills  names of skills already learned, so the reviewer proposes
 *                        a patch instead of a near-duplicate
 * @param existingMemory  current memory content, for the same reason
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public record ReviewInput(
        String tenantId,
        String agentId,
        String sessionKey,
        String transcript,
        List<String> existingSkills,
        String existingMemory
) {
    public ReviewInput {
        existingSkills = existingSkills == null ? List.of() : List.copyOf(existingSkills);
    }
}

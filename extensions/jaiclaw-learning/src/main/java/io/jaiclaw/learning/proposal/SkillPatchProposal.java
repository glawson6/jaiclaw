package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Proposes a targeted edit to an existing learned skill.
 *
 * <p>Applied as a <strong>unique exact-span replace</strong>: if {@code findText}
 * does not appear exactly once in the current body, the patch is rejected rather
 * than guessed at. Ambiguity here would silently rewrite the wrong part of a
 * skill that then steers every future session.
 *
 * @param skillName skill to patch
 * @param findText  span to replace; must occur exactly once
 * @param replaceText replacement
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public record SkillPatchProposal(
        String id,
        String tenantId,
        String originSessionKey,
        Instant createdAt,
        ProposalState state,
        String summary,
        String skillName,
        String findText,
        String replaceText
) implements Proposal {

    public SkillPatchProposal {
        if (state == null) state = ProposalState.PENDING;
        if (createdAt == null) createdAt = Instant.now();
    }

    @Override
    public ProposalKind kind() {
        return ProposalKind.SKILL_PATCH;
    }

    @Override
    public String contentHash() {
        return Proposal.hashOf("skill_patch", tenantId, skillName, findText, replaceText);
    }

    @Override
    public Proposal withState(ProposalState newState) {
        return new SkillPatchProposal(id, tenantId, originSessionKey, createdAt,
                newState, summary, skillName, findText, replaceText);
    }
}

package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Proposes creating a new learned skill.
 *
 * @param skillName   directory-safe skill name
 * @param description one-line description for the skill's frontmatter
 * @param body        the SKILL.md body (instructions, not frontmatter)
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public record SkillProposal(
        String id,
        String tenantId,
        String originSessionKey,
        Instant createdAt,
        ProposalState state,
        String summary,
        String skillName,
        String description,
        String body
) implements Proposal {

    public SkillProposal {
        if (state == null) state = ProposalState.PENDING;
        if (createdAt == null) createdAt = Instant.now();
    }

    @Override
    public ProposalKind kind() {
        return ProposalKind.SKILL;
    }

    @Override
    public String contentHash() {
        return Proposal.hashOf("skill", tenantId, skillName, body);
    }

    @Override
    public Proposal withState(ProposalState newState) {
        return new SkillProposal(id, tenantId, originSessionKey, createdAt,
                newState, summary, skillName, description, body);
    }
}

package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.model.MemoryScope;

import java.time.Instant;

/**
 * Proposes adding or replacing a memory entry.
 *
 * @param scope   which memory the entry belongs in; defaults to AGENT
 * @param heading markdown section heading the entry lives under
 * @param content the entry itself
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public record MemoryProposal(
        String id,
        String tenantId,
        String originSessionKey,
        Instant createdAt,
        ProposalState state,
        String summary,
        MemoryScope scope,
        String heading,
        String content
) implements Proposal {

    public MemoryProposal {
        if (state == null) state = ProposalState.PENDING;
        if (scope == null) scope = MemoryScope.AGENT;
        if (createdAt == null) createdAt = Instant.now();
    }

    @Override
    public ProposalKind kind() {
        return ProposalKind.MEMORY;
    }

    @Override
    public String contentHash() {
        return Proposal.hashOf("memory", tenantId, scope.name(), heading, content);
    }

    @Override
    public Proposal withState(ProposalState newState) {
        return new MemoryProposal(id, tenantId, originSessionKey, createdAt,
                newState, summary, scope, heading, content);
    }
}

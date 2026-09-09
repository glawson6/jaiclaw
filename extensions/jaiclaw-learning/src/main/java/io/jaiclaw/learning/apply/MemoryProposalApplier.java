package io.jaiclaw.learning.apply;

import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.hook.event.MemoryUpdatedEvent;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.learning.LearningProperties;
import io.jaiclaw.learning.proposal.MemoryProposal;
import io.jaiclaw.learning.proposal.Proposal;
import io.jaiclaw.learning.proposal.ProposalService;
import io.jaiclaw.learning.proposal.SkillPatchProposal;
import io.jaiclaw.learning.proposal.SkillProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Applies {@link MemoryProposal}s by appending to the agent's memory document.
 *
 * <p>Appends under a markdown heading rather than replacing the document: memory
 * accumulated over months is the user's, and a learning pass that overwrote it
 * wholesale could destroy far more than it added.
 *
 * <p>Skill proposals are refused here with a clear message rather than silently
 * ignored — 4B adds the skill workshop that handles them.
 *
 * <p>Phase 4A of the 1.2.0 plan.
 */
@Experimental
public class MemoryProposalApplier implements ProposalApplier {

    private static final Logger log = LoggerFactory.getLogger(MemoryProposalApplier.class);

    /** Default budget when creating a memory document from nothing. */
    private static final int DEFAULT_CHAR_BUDGET = 8_000;

    private final AgentMindMemoryProvider memoryProvider;
    private final ProposalService proposals;
    private final LearningProperties properties;
    private final AgentHookDispatcher hooks;

    public MemoryProposalApplier(AgentMindMemoryProvider memoryProvider,
                                 ProposalService proposals,
                                 LearningProperties properties,
                                 AgentHookDispatcher hooks) {
        this.memoryProvider = memoryProvider;
        this.proposals = proposals;
        this.properties = properties;
        this.hooks = hooks;
    }

    @Override
    public ApplyResult applyIfEligible(Proposal proposal, String actor) {
        if (proposal == null) return ApplyResult.refused("No proposal given.");

        return switch (proposal) {
            case MemoryProposal m -> applyMemory(m, actor);
            case SkillProposal s -> ApplyResult.refused(
                    "Skill proposals require the skill workshop, which is not enabled.");
            case SkillPatchProposal s -> ApplyResult.refused(
                    "Skill patches require the skill workshop, which is not enabled.");
        };
    }

    private ApplyResult applyMemory(MemoryProposal proposal, String actor) {
        if (memoryProvider == null) {
            // Degrade to a clear refusal rather than failing startup when no
            // memory provider is on the classpath.
            return ApplyResult.refused(
                    "No memory provider is configured, so memory proposals cannot be applied.");
        }
        // In auto mode this is called without an operator; in propose mode the
        // caller has already decided. Either way, honour the mode.
        if ("auto".equals(actor) && !properties.isAuto()) {
            return ApplyResult.refused("Learning is in propose mode; an operator must apply this.");
        }

        try {
            String agentId = agentIdFrom(proposal.originSessionKey());
            Optional<MemoryDocument> existing = memoryProvider.findMemory(
                    proposal.tenantId(), proposal.scope(), agentId, null);

            String addition = "\n## " + proposal.heading() + "\n\n" + proposal.content().strip() + "\n";
            MemoryDocument updated;
            if (existing.isPresent()) {
                MemoryDocument doc = existing.get();
                updated = doc.withContent((doc.content() == null ? "" : doc.content()) + addition);
            } else {
                updated = MemoryDocument.forAgent(proposal.tenantId(), agentId,
                        addition.strip(), DEFAULT_CHAR_BUDGET);
            }
            MemoryDocument saved = memoryProvider.saveMemory(updated);

            proposals.markApplied(proposal.tenantId(), proposal.id(), actor);
            fireMemoryUpdated(proposal, agentId, saved);
            log.info("Applied memory proposal {} for tenant {} (heading '{}')",
                    proposal.id(), proposal.tenantId(), proposal.heading());
            return ApplyResult.ok("Memory updated under '" + proposal.heading() + "'.");

        } catch (RuntimeException e) {
            // The proposal stays PENDING — a failed apply must not look like a success.
            log.warn("Failed to apply memory proposal {}", proposal.id(), e);
            return ApplyResult.refused("Could not update memory: " + e.getMessage());
        }
    }

    @Override
    public ApplyResult rollback(Proposal proposal, String actor) {
        // Memory rollback needs the ledger's content-addressed blobs, which land
        // in 4B alongside the skill workshop. Refusing clearly beats a partial
        // restore that silently loses the rest of the document.
        return ApplyResult.refused(
                "Memory rollback requires the learning ledger, which is not enabled.");
    }

    private void fireMemoryUpdated(MemoryProposal proposal, String agentId, MemoryDocument saved) {
        if (hooks == null) return;
        try {
            hooks.fireVoid(MemoryUpdatedEvent.ofAgent(
                    proposal.tenantId(), agentId, saved.version(), "learning"));
        } catch (RuntimeException e) {
            log.debug("MemoryUpdatedEvent listener failed", e);
        }
    }

    /** Session keys are {@code agentId:channel:account:peer}; the agent is the first segment. */
    private static String agentIdFrom(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) return "default";
        int colon = sessionKey.indexOf(':');
        return colon > 0 ? sessionKey.substring(0, colon) : sessionKey;
    }
}

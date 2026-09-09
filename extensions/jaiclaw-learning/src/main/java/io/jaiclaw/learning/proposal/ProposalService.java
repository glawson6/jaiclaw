package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The decision surface over {@link ProposalStore}: submit, apply, reject, roll back.
 *
 * <p>Enforces two invariants the store itself does not:
 * <ul>
 *   <li><strong>Dedupe by content hash</strong> — a reviewer that keeps noticing
 *       the same pattern must not grow an unreadable queue.</li>
 *   <li><strong>Legal transitions only</strong> — see {@link ProposalState}. In
 *       particular a REJECTED proposal cannot be revived, because re-deciding an
 *       operator's rejection is precisely the autonomy this module avoids.</li>
 * </ul>
 *
 * <p>Applying is delegated to a {@link io.jaiclaw.learning.apply.ProposalApplier}
 * by the caller; this service owns only state. That split keeps "did the write
 * succeed" and "is the proposal marked applied" separable, so a failed apply
 * leaves the proposal PENDING rather than lying about what happened.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final ProposalStore store;

    public ProposalService(ProposalStore store) {
        this.store = store;
    }

    /**
     * Submits a proposal, assigning an id if it has none.
     *
     * @return the stored proposal, or empty when it duplicated an existing one
     */
    public Optional<Proposal> submit(Proposal proposal) {
        if (proposal == null) return Optional.empty();
        if (store.existsByContentHash(proposal.tenantId(), proposal.contentHash())) {
            log.debug("Dropping duplicate {} proposal for tenant {} (hash {})",
                    proposal.kind(), proposal.tenantId(), shortHash(proposal.contentHash()));
            return Optional.empty();
        }
        Proposal toStore = proposal.id() == null || proposal.id().isBlank()
                ? withId(proposal, UUID.randomUUID().toString())
                : proposal;
        store.save(toStore);
        log.info("Learning proposal submitted — kind={} tenant={} id={} summary={}",
                toStore.kind(), toStore.tenantId(), toStore.id(), toStore.summary());
        return Optional.of(toStore);
    }

    public List<Proposal> list(String tenantId) {
        return store.list(tenantId);
    }

    public List<Proposal> list(String tenantId, ProposalState state) {
        return store.list(tenantId, state);
    }

    public Optional<Proposal> find(String tenantId, String id) {
        return store.find(tenantId, id);
    }

    /**
     * Marks a proposal applied. Call only after the underlying write succeeded.
     *
     * @return the updated proposal, or empty if it was missing or not PENDING
     */
    public Optional<Proposal> markApplied(String tenantId, String id, String actor) {
        return transition(tenantId, id, ProposalState.APPLIED, actor);
    }

    /** Rejects a proposal. Terminal. */
    public Optional<Proposal> reject(String tenantId, String id, String reason, String actor) {
        Optional<Proposal> result = transition(tenantId, id, ProposalState.REJECTED, actor);
        result.ifPresent(p -> log.info("Learning proposal {} rejected by {} — {}",
                id, actor, reason == null ? "(no reason given)" : reason));
        return result;
    }

    /** Marks an applied proposal rolled back. Call only after the restore succeeded. */
    public Optional<Proposal> markRolledBack(String tenantId, String id, String actor) {
        return transition(tenantId, id, ProposalState.ROLLED_BACK, actor);
    }

    private Optional<Proposal> transition(String tenantId, String id,
                                          ProposalState next, String actor) {
        Optional<Proposal> found = store.find(tenantId, id);
        if (found.isEmpty()) {
            log.warn("Cannot transition unknown proposal {} for tenant {}", id, tenantId);
            return Optional.empty();
        }
        Proposal current = found.get();
        if (!current.state().canTransitionTo(next)) {
            log.warn("Illegal proposal transition {} -> {} for {} (tenant {})",
                    current.state(), next, id, tenantId);
            return Optional.empty();
        }
        Proposal updated = current.withState(next);
        store.save(updated);
        log.info("Learning proposal {} {} by {}", id, next, actor == null ? "system" : actor);
        return Optional.of(updated);
    }

    private static Proposal withId(Proposal p, String id) {
        return switch (p) {
            case MemoryProposal m -> new MemoryProposal(id, m.tenantId(), m.originSessionKey(),
                    m.createdAt(), m.state(), m.summary(), m.scope(), m.heading(), m.content());
            case SkillProposal s -> new SkillProposal(id, s.tenantId(), s.originSessionKey(),
                    s.createdAt(), s.state(), s.summary(), s.skillName(), s.description(), s.body());
            case SkillPatchProposal s -> new SkillPatchProposal(id, s.tenantId(), s.originSessionKey(),
                    s.createdAt(), s.state(), s.summary(), s.skillName(), s.findText(), s.replaceText());
        };
    }

    private static String shortHash(String hash) {
        return hash == null ? "?" : hash.substring(0, Math.min(12, hash.length()));
    }
}

package io.jaiclaw.learning.proposal;

import io.jaiclaw.core.api.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped persistence for learning proposals.
 *
 * <p>Every method takes a tenant explicitly rather than reading
 * {@code TenantContextHolder}: the reviewer runs on a background thread, and a
 * store that silently resolved "the current tenant" there would be one missed
 * propagator away from writing one tenant's proposals into another's queue.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public interface ProposalStore {

    /** Persists a proposal. Implementations must write atomically. */
    void save(Proposal proposal);

    /** Finds one by id within a tenant. */
    Optional<Proposal> find(String tenantId, String id);

    /** All proposals for a tenant, newest first. */
    List<Proposal> list(String tenantId);

    /** Proposals for a tenant in the given state, newest first. */
    List<Proposal> list(String tenantId, ProposalState state);

    /**
     * Whether this tenant already has a non-rejected proposal with the same
     * content hash. Used to drop repeat suggestions.
     */
    boolean existsByContentHash(String tenantId, String contentHash);

    /** Removes a proposal. Primarily for tests and operator cleanup. */
    boolean delete(String tenantId, String id);
}

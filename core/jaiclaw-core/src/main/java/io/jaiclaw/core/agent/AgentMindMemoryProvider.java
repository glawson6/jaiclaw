package io.jaiclaw.core.agent;

import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;

import java.util.Optional;

/**
 * SPI for loading and saving {@link MemoryDocument}s — bounded markdown blobs
 * spliced into the system prompt at session start.
 *
 * <p>Implementations MUST handle all three scope values ({@link MemoryScope#TENANT},
 * {@link MemoryScope#AGENT}, {@link MemoryScope#PEER}) or explicitly document
 * the unsupported set — scope dispatch lives inside the provider (no parallel
 * {@code TenantMemoryProvider} SPI). See AgentMind analysis §6.
 *
 * <p>Writes follow optimistic CAS via {@link MemoryDocument#version()}.
 * Implementations MUST reject stale writes with
 * {@link StaleMemoryVersionException}. Writes whose new content exceeds
 * {@link MemoryDocument#charBudget()} MUST raise
 * {@link MemoryOverflowException} so the LLM is forced to consolidate
 * in-turn (the "error-as-control-flow" pattern).
 *
 * <p>This SPI is distinct from the existing search-shaped
 * {@link MemoryProvider}: that interface returns a {@code String} of
 * concatenated memory content for a workspace directory; this one is
 * keyed on the (scope, tenant, agent, peer) tuple and returns the
 * structured record.
 */
public interface AgentMindMemoryProvider {

    /**
     * Look up a Memory document by (tenant, scope, agent, peer).
     *
     * @param tenantId required for all scopes
     * @param scope    required
     * @param agentId  required for AGENT and PEER scopes; must be null for TENANT
     * @param peerId   required for PEER scope; must be null otherwise
     * @return the stored document, or {@link Optional#empty()} if none exists
     */
    Optional<MemoryDocument> findMemory(String tenantId, MemoryScope scope, String agentId, String peerId);

    /**
     * Atomically write a Memory. Implementation MUST reject stale-version
     * writes with {@link StaleMemoryVersionException} and overflow writes
     * with {@link MemoryOverflowException}.
     *
     * @return the persisted document
     */
    MemoryDocument saveMemory(MemoryDocument document);

    /** Delete the Memory matching the given key. No-op if missing. */
    void deleteMemory(String tenantId, MemoryScope scope, String agentId, String peerId);
}

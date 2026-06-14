package io.jaiclaw.core.agent;

import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;

import java.util.Optional;

/**
 * SPI for loading and saving {@link Soul} markdown overlays.
 *
 * <p>Implementations MUST handle both {@link SoulScope#TENANT} and
 * {@link SoulScope#AGENT} or explicitly document the unsupported set —
 * scope dispatch lives inside the provider (no parallel {@code TenantSoulProvider}
 * SPI). See AgentMind analysis §6.
 *
 * <p>Writes follow optimistic CAS via {@link Soul#version()}. Implementations
 * MUST reject writes whose version is not strictly greater than the stored
 * version with a {@link StaleSoulVersionException}.
 */
public interface SoulProvider {

    /**
     * Look up a Soul by tenant + scope (+ agentId for AGENT scope).
     *
     * @param tenantId required
     * @param scope    required; {@link SoulScope#TENANT} or {@link SoulScope#AGENT}
     * @param agentId  required for AGENT scope, must be {@code null} for TENANT scope
     * @return the stored Soul, or {@link Optional#empty()} if none exists
     */
    Optional<Soul> findSoul(String tenantId, SoulScope scope, String agentId);

    /**
     * Atomically write a Soul. Implementation MUST reject writes whose
     * {@code soul.version()} is not strictly greater than the stored version.
     *
     * @return the persisted Soul (its version field will match {@code soul.version()})
     * @throws StaleSoulVersionException if the write was rejected as stale
     */
    Soul saveSoul(Soul soul);

    /**
     * Delete the Soul matching the given key. No-op if it does not exist.
     */
    void deleteSoul(String tenantId, SoulScope scope, String agentId);
}

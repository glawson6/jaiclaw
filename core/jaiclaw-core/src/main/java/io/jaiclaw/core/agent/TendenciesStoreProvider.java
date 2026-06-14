package io.jaiclaw.core.agent;

import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;

import java.util.Collection;
import java.util.Optional;

/**
 * SPI for loading and saving {@link Tendencies} records.
 *
 * <p>Implementations MUST handle both {@link TendenciesScope#USER} (per-user
 * learned representation) and {@link TendenciesScope#TENANT} (org-wide
 * rollup; Phase 5). Scope dispatch lives inside the provider — no parallel
 * {@code TenantTendenciesStoreProvider} SPI. See AgentMind analysis §6.
 *
 * <p>Writes follow optimistic CAS via {@link Tendencies#version()}.
 * Implementations MUST reject stale writes with
 * {@link StaleTendenciesVersionException}. The striped dialectic executor
 * normally avoids CAS contention by serialising writes per
 * {@code (tenantId, canonicalUserId)}.
 *
 * <p>The {@link #findActiveUsers} method supports the Phase 5 tenant
 * rollup pipeline — it lists USER-scope records whose
 * {@code updatedAt} falls within the active-users window. Default JSON
 * impl scans the directory tree; SQL impls index on
 * {@code (tenant_id, updated_at DESC)}.
 */
public interface TendenciesStoreProvider {

    /**
     * Returns the backend type identifier (e.g. {@code "json"}, {@code "h2"},
     * {@code "redis"}). Selectable via
     * {@code jaiclaw.agentmind.tendencies.storage.type}.
     */
    String type();

    /**
     * Look up a Tendencies record by (tenantId, scope, canonicalUserId).
     *
     * @param tenantId         required for all scopes
     * @param scope            required
     * @param canonicalUserId  required for USER scope; must be null for TENANT
     * @return stored record, or {@link Optional#empty()} if none exists
     */
    Optional<Tendencies> findTendencies(String tenantId, TendenciesScope scope, String canonicalUserId);

    /**
     * Atomically write a Tendencies record. Rejects stale-version writes
     * with {@link StaleTendenciesVersionException}.
     *
     * @return the persisted record
     */
    Tendencies saveTendencies(Tendencies record);

    /** Delete the record matching the given key. No-op if missing. */
    void deleteTendencies(String tenantId, TendenciesScope scope, String canonicalUserId);

    /**
     * List USER-scope records in a tenant whose {@code updatedAt} is greater
     * than or equal to {@code sinceEpochMillis}. Powers the Phase 5 tenant
     * rollup pipeline; ordering is implementation-defined but a consistent
     * snapshot is required.
     */
    Collection<Tendencies> findActiveUsers(String tenantId, long sinceEpochMillis);
}

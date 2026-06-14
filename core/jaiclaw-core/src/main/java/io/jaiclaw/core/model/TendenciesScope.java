package io.jaiclaw.core.model;

/**
 * Scope discriminator for {@link Tendencies} records.
 *
 * <p>{@link #USER} is the per-(tenant, user) learned representation — observed
 * patterns from the user's recent transcript window, computed by a
 * cadence-gated dialectic pass. {@link #TENANT} is the org-wide aggregate
 * rolled up from all active users in a tenant (Phase 5).
 *
 * <p>Renders in the user message in most-general → most-specific order:
 * {@code <tenant-tendencies>}...{@code </tenant-tendencies>} (TENANT, when
 * present) → {@code <tendencies-context>}...{@code </tendencies-context>}
 * (USER). Empty / absent records are omitted entirely.
 */
public enum TendenciesScope {
    /** One record per tenant — rolled up from user records by a daily pipeline. */
    TENANT,

    /** One record per (tenant, user) — learned from the user's recent transcript window. */
    USER
}

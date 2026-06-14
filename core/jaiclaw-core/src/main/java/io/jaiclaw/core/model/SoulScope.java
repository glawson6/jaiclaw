package io.jaiclaw.core.model;

/**
 * Scope discriminator for {@link Soul} records.
 *
 * <p>{@link #TENANT} is an org-wide, operator-authored personality overlay
 * inherited by every agent in the tenant. {@link #AGENT} is a per-agent
 * personality the {@code soul} agent tool can mutate.
 *
 * <p>Renders in this order in the system prompt (most-general to most-specific):
 * {@code TENANT} → {@code AGENT}. Empty / absent records are omitted entirely
 * — no placeholder headers — to preserve prefix-cache stability.
 */
public enum SoulScope {
    /** One Soul per tenant. Operator-authored, agents cannot write. */
    TENANT,

    /** One Soul per (tenant, agent). Mutable via the {@code soul} agent tool. */
    AGENT
}

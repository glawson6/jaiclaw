package io.jaiclaw.core.model;

/**
 * Scope discriminator for {@link MemoryDocument} records.
 *
 * <p>Hermes' {@code MEMORY.md} (agent-scope) and {@code USER.md}
 * (peer-scope) carry over verbatim; JaiClaw adds {@link #TENANT} for
 * org-wide institutional knowledge that every agent and user inherits.
 *
 * <p>Renders in the system prompt in most-general → most-specific order:
 * {@code TENANT} → {@code AGENT} → {@code PEER}. Empty / absent records
 * are omitted entirely to preserve prefix-cache stability.
 */
public enum MemoryScope {
    /** One Memory per tenant — institutional knowledge shared across all agents and users. */
    TENANT,

    /** One Memory per (tenant, agent) — analogous to hermes MEMORY.md. */
    AGENT,

    /** One Memory per (tenant, agent, peer/user) — analogous to hermes USER.md. */
    PEER
}

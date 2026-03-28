package io.jaiclaw.core.tenant;

/**
 * JaiClaw tenant operating mode.
 *
 * <ul>
 *   <li>{@code SINGLE} — One organization, one data space. Personal assistant, team bot,
 *       departmental agent, or single-company deployment. May have many users with JWT-based
 *       authentication, roles, and authorization — but all users share the same organizational
 *       data boundary. No data partitioning by organization.</li>
 *   <li>{@code MULTI} — Platform mode. Multiple organizations sharing a single deployment,
 *       each with strictly isolated data. Fail-closed: missing tenant context = exception.</li>
 * </ul>
 *
 * <p>Authentication mode ({@code jaiclaw.security.mode}) is orthogonal to tenant mode.
 * JWT in SINGLE mode provides user identity/roles, not tenant partitioning.
 */
public enum TenantMode {
    /** One organization, one data space. */
    SINGLE,
    /** Multiple organizations, strict isolation. */
    MULTI
}

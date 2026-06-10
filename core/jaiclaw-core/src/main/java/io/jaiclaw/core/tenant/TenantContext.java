package io.jaiclaw.core.tenant;

import io.jaiclaw.core.api.Stable;

import java.util.Map;

/**
 * Represents the current tenant (e.g., a coaching program) for multi-tenant isolation.
 * Every inbound request must resolve to a TenantContext before any agent execution,
 * memory access, or tool call occurs.
 *
 * <p>0.8.0 P3.5: {@link Stable} — the multi-tenant hardening work in
 * 0.7.x locked this surface.
 */
@Stable
public interface TenantContext {

    /**
     * Unique tenant identifier (e.g., programId UUID).
     */
    String getTenantId();

    /**
     * Human-readable tenant name (e.g., "University of Georgia Football").
     */
    String getTenantName();

    /**
     * Arbitrary metadata associated with the tenant — subscription tier, sport, division, etc.
     */
    Map<String, Object> getMetadata();
}

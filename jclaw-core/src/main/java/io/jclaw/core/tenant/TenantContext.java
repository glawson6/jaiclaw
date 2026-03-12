package io.jclaw.core.tenant;

import java.util.Map;

/**
 * Represents the current tenant (e.g., a coaching program) for multi-tenant isolation.
 * Every inbound request must resolve to a TenantContext before any agent execution,
 * memory access, or tool call occurs.
 */
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

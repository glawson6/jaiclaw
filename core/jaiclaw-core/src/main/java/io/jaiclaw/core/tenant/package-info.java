/**
 * Tenant isolation primitives. {@link io.jaiclaw.core.tenant.TenantGuard},
 * {@link io.jaiclaw.core.tenant.TenantContext},
 * {@link io.jaiclaw.core.tenant.TenantContextHolder}, and
 * {@link io.jaiclaw.core.tenant.TenantContextPropagator} are
 * {@link io.jaiclaw.core.api.Stable} — they're the audited surface
 * that the 0.7.x multi-tenant hardening work locked down.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.core.tenant;

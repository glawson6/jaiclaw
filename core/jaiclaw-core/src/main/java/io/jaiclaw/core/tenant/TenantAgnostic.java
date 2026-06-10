package io.jaiclaw.core.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code Map}/{@code ConcurrentHashMap}/{@code Set} field as
 * intentionally tenant-agnostic.
 *
 * <p>The {@code TenantIsolationGuardSpec} regression test scans
 * production source for {@code ConcurrentHashMap<String, ...>} and
 * {@code HashMap<String, ...>} field declarations under the modules
 * audited for multi-tenant isolation. Any such field that holds
 * business data must route its keys through
 * {@link TenantGuard#resolveStorageKey(String)} (or another
 * tenant-aware key construction). Fields whose content is genuinely
 * shared across tenants — channel adapter registries, tool
 * definitions, plugin registries, bounded scratch caches — can carry
 * this annotation to opt out of the check.
 *
 * <p>The {@link #reason()} string is mandatory and surfaces in the
 * failure message when a reviewer questions a particular usage.
 *
 * <p>The annotation is {@link RetentionPolicy#SOURCE} only — it exists
 * for static analysis, not for runtime introspection.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface TenantAgnostic {
    /** Explain why this field doesn't need tenant scoping. */
    String reason();
}

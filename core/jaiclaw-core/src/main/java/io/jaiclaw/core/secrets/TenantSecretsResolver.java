package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;
import io.jaiclaw.core.tenant.TenantGuard;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-scoped secrets lookup. Delegates to a {@link SecretsResolver}
 * with optional per-tenant key prefixing.
 *
 * <h2>Resolution strategy</h2>
 *
 * In <b>SINGLE</b> tenant mode, this resolver is a pass-through over
 * the underlying chain — the key is looked up verbatim.
 *
 * In <b>MULTI</b> tenant mode, the resolver first tries
 * {@code "<tenantId>:<key>"} (the tenant-scoped form), then falls back
 * to {@code "<key>"} (the shared form). This lets common application
 * secrets (e.g., a webhook signing key shared by all tenants) live
 * unprefixed while still allowing per-tenant overrides (e.g., each
 * tenant's own Anthropic key) to take precedence when configured.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public final class TenantSecretsResolver {

    private final SecretsResolver delegate;
    private final TenantGuard guard;

    public TenantSecretsResolver(SecretsResolver delegate, TenantGuard guard) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    /**
     * Resolve a secret, scoped to the current tenant if multi-tenant
     * mode is active. See class-level docs for the resolution strategy.
     */
    public SecretResolution resolve(String key) {
        Objects.requireNonNull(key, "key");
        if (guard.isMultiTenant()) {
            String tenantId = guard.resolveStoragePrefix();
            String scopedKey = tenantId + ":" + key;
            SecretResolution scoped = delegate.resolve(scopedKey);
            if (!(scoped instanceof SecretResolution.Missing)) {
                // Tenant-scoped value found (or provider error) — return it.
                // Re-key Resolved/ProviderError to report the user's original key.
                return rekey(scoped, key);
            }
        }
        return delegate.resolve(key);
    }

    /**
     * Convenience: resolve and return the value if {@link
     * SecretResolution.Resolved Resolved}, otherwise empty.
     */
    public Optional<String> getValue(String key) {
        return resolve(key) instanceof SecretResolution.Resolved r
                ? Optional.of(r.value())
                : Optional.empty();
    }

    /**
     * Get all secrets matching a prefix, scoped to the current tenant.
     * In MULTI mode the prefix is itself prefixed with {@code <tenantId>:}.
     */
    public Map<String, String> getAll(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (guard.isMultiTenant()) {
            String tenantId = guard.resolveStoragePrefix();
            return delegate.getAll(tenantId + ":" + prefix);
        }
        return delegate.getAll(prefix);
    }

    private static SecretResolution rekey(SecretResolution resolution, String userKey) {
        return switch (resolution) {
            case SecretResolution.Resolved r ->
                    new SecretResolution.Resolved(userKey, r.value(), r.providerName());
            case SecretResolution.ProviderError pe ->
                    new SecretResolution.ProviderError(userKey, pe.providerName(), pe.cause());
            case SecretResolution.Missing m ->
                    new SecretResolution.Missing(userKey);
        };
    }
}

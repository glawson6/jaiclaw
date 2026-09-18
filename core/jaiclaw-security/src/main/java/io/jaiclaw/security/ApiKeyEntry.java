package io.jaiclaw.security;

/**
 * One configured API key.
 *
 * <p>A key is a complete, single-purpose credential: it names exactly one
 * tenant and grants exactly one role. See {@link ApiKeyStore} for why the
 * cardinality is fixed at one-to-one.
 *
 * @param name      operator-facing name, safe to log. Never the key itself.
 * @param key       the raw key material. Typically supplied as a
 *                  {@code ${ENV_VAR}} placeholder rather than inlined.
 *                  Mutually exclusive with {@link #secretRef()}.
 * @param secretRef a {@code provider://vault/item/field} reference resolved
 *                  through {@code io.jaiclaw.core.secrets.SecretsResolver}.
 *                  Mutually exclusive with {@link #key()}.
 * @param tenantId  the single tenant this key may act for. Required in
 *                  multi-tenant mode; a key without one can never authenticate
 *                  there, because there is no association to check the
 *                  {@code X-Tenant-Id} header against.
 * @param role      the single authority granted, e.g. {@code jaiclaw.admin}.
 *                  <strong>Required</strong> — a key with no role aborts
 *                  startup rather than silently authenticating with none.
 *
 * <p>An entry with no resolvable key material is
 * {@linkplain #hasKeyMaterial() refused} rather than accepted, following the
 * same fail-closed stance as {@code WebhookRoute.isUsable()}.
 *
 * <p>1.2.0.
 */
public record ApiKeyEntry(
        String name,
        String key,
        String secretRef,
        String tenantId,
        String role
) {
    public ApiKeyEntry {
        if (name == null || name.isBlank()) name = "unnamed";
        if (key != null && key.isBlank()) key = null;
        if (secretRef != null && secretRef.isBlank()) secretRef = null;
        if (tenantId != null && tenantId.isBlank()) tenantId = null;
        if (role != null && role.isBlank()) role = null;
    }

    /**
     * Whether some key material was configured. An entry with neither
     * {@code key} nor {@code secret-ref} is a configuration error — it is
     * refused at startup rather than silently ignored, because a key that
     * cannot be matched is indistinguishable from one that was never meant
     * to exist.
     */
    public boolean hasKeyMaterial() {
        return key != null || secretRef != null;
    }
}

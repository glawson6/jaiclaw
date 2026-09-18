package io.jaiclaw.security;

import java.util.Optional;

/**
 * SPI answering one question: <em>is this API key one we know about, and which
 * tenant and role is it bound to?</em>
 *
 * <p>The reference implementation {@link ConfigApiKeyStore} resolves from
 * {@code jaiclaw.security.api-keys[]} and lives in this module; adopters can
 * plug in Redis / JDBC / cloud-secret backends by providing their own
 * {@code @Bean ApiKeyStore}. A later {@code jaiclaw-security-redis} module is
 * expected to supply a Redis-backed implementation selected by
 * {@code jaiclaw.security.api-key-store.backend=redis}.
 *
 * <h2>Cardinality</h2>
 * A key is a complete, single-purpose credential: <strong>one key maps to
 * exactly one tenant and exactly one role</strong>. There are deliberately no
 * multi-tenant or multi-role keys — a caller needing authority in two tenants,
 * or two roles within one tenant, holds two keys. That keeps revocation to a
 * single delete and bounds the blast radius of a leaked key to one
 * (tenant, role) pair.
 *
 * <h2>Two-step resolution</h2>
 * Lookup deliberately does <em>not</em> take a tenant. The caller resolves the
 * identity first and compares the tenant itself, so the two failure modes stay
 * distinguishable:
 * <ul>
 *   <li>unknown key → {@link Optional#empty()} → <strong>401</strong></li>
 *   <li>known key, wrong tenant → identity present, tenant mismatch →
 *       <strong>403</strong></li>
 * </ul>
 *
 * <h2>Implementation requirements</h2>
 * Implementations MUST compare key material in constant time and MUST NOT
 * short-circuit on a partial match — see {@link ConfigApiKeyStore} for the
 * hash-index approach used by the reference implementation.
 *
 * <p>1.2.0.
 */
public interface ApiKeyStore {

    /**
     * Look up the identity bound to a presented key.
     *
     * @param presentedKey the raw key supplied by the caller; may be {@code null} or blank
     * @return the bound identity, or empty when the key is unknown
     */
    Optional<ApiKeyIdentity> findByKey(String presentedKey);

    /** How many keys this store holds, for startup logging and diagnostics. */
    default int size() {
        return -1;
    }

    /**
     * The identity a known API key carries.
     *
     * @param keyName  operator-facing name, safe to log (never the key itself)
     * @param tenantId the single tenant this key may act for; {@code null} when
     *                 the key declares none, which makes it unusable in
     *                 multi-tenant mode
     * @param role     the single authority this key grants; {@code null} only
     *                 for the legacy single-key path
     */
    record ApiKeyIdentity(String keyName, String tenantId, String role) {

        public ApiKeyIdentity {
            if (keyName == null || keyName.isBlank()) keyName = "unnamed";
            if (tenantId != null && tenantId.isBlank()) tenantId = null;
            if (role != null && role.isBlank()) role = null;
        }

        /** Whether this key declares a tenant, and so can be used in multi-tenant mode. */
        public boolean hasTenant() {
            return tenantId != null;
        }
    }
}

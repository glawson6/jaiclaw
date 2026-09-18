package io.jaiclaw.core.model;

import java.time.Instant;

/**
 * Links a platform-specific user identity to a canonical user ID.
 *
 * <h2>Verified vs. asserted links</h2>
 * A link is <strong>verified</strong> when both {@code issuer} and
 * {@code verifiedAt} are present: someone proved control of the external
 * identity, typically by completing an OAuth authorization-code flow. A link
 * with either field null is <strong>asserted</strong> — it was created by
 * calling {@code link()} directly, with nothing proving the binding.
 *
 * <p>The distinction matters because authorization decisions must key on
 * verification. An asserted link is fine for cross-channel conversational
 * continuity; it is not a basis for granting access to a tenant's data.
 * Use {@link #isVerified()} rather than inspecting the fields.
 *
 * @param canonicalUserId UUID of the "real" user across channels. For a verified
 *                        link this is the external identity provider's subject.
 * @param channel         platform identifier ("telegram", "slack", etc.)
 * @param channelUserId   user's ID on that platform
 * @param tenantId        tenant scope (nullable for single-tenant)
 * @param issuer          identity provider that vouched for this link — the OIDC
 *                        issuer URI. Null for asserted links.
 * @param verifiedAt      when control of the external identity was proven.
 *                        Null for asserted links.
 */
public record IdentityLink(
        String canonicalUserId,
        String channel,
        String channelUserId,
        String tenantId,
        String issuer,
        Instant verifiedAt
) {
    /** Backward-compatible constructor without tenantId. Creates an asserted link. */
    public IdentityLink(String canonicalUserId, String channel, String channelUserId) {
        this(canonicalUserId, channel, channelUserId, null, null, null);
    }

    /** Backward-compatible constructor without verification. Creates an asserted link. */
    public IdentityLink(String canonicalUserId, String channel, String channelUserId,
                        String tenantId) {
        this(canonicalUserId, channel, channelUserId, tenantId, null, null);
    }

    public IdentityLink {
        if (issuer != null && issuer.isBlank()) issuer = null;
    }

    /**
     * Whether control of the external identity was actually proven.
     *
     * <p>Both fields are required: an issuer without a timestamp means the
     * provenance was recorded but the proof was not, which is not good enough
     * to act on.
     */
    public boolean isVerified() {
        return issuer != null && verifiedAt != null;
    }

    /** A verified link, as produced by a completed authorization-code flow. */
    public static IdentityLink verified(String subject, String channel, String channelUserId,
                                        String tenantId, String issuer, Instant verifiedAt) {
        return new IdentityLink(subject, channel, channelUserId, tenantId, issuer, verifiedAt);
    }
}

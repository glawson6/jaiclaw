package io.jaiclaw.hermes.soul.user;

/**
 * Resolves a stable per-user key for hermes storage from channel + peerId pairs.
 *
 * <p>The resolved key is the second-level dimension of every per-user store
 * lookup (the first being tenantId). Implementations decide whether a
 * canonical {@code IdentityLink} UUID or a deterministic fallback hash is the
 * right answer for a given deployment.
 *
 * <p>Plan §5 task 1.4 — SPI. The default impl is
 * {@link IdentityLinkUserKeyResolver}. When {@code jaiclaw-identity} is
 * absent from the classpath, the resolver falls back to a deterministic
 * SHA-256-derived hash so same-channel continuity still works without
 * cross-channel linkage.
 */
public interface HermesUserKeyResolver {

    /**
     * Resolve a stable, lowercase hex (or alphanumeric) user key for the
     * given channel + peer.
     *
     * @param channel non-null channel id (e.g. "telegram", "slack")
     * @param peerId  non-null channel-specific peer id (e.g. a Slack user ID)
     * @return non-null, non-empty user key
     */
    String resolve(String channel, String peerId);

    /**
     * Returns {@code true} if this resolver is currently producing keys via
     * its linked-identity path (e.g. {@code IdentityResolver}) and not the
     * deterministic fallback. Used by Actuator counters + startup logging.
     */
    default boolean isLinkedKeyAvailable() {
        return false;
    }
}

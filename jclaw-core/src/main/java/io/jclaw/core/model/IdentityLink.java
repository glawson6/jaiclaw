package io.jclaw.core.model;

/**
 * Links a platform-specific user identity to a canonical user ID.
 *
 * @param canonicalUserId  UUID of the "real" user across channels
 * @param channel          platform identifier ("telegram", "slack", etc.)
 * @param channelUserId    user's ID on that platform
 */
public record IdentityLink(
        String canonicalUserId,
        String channel,
        String channelUserId
) {
}

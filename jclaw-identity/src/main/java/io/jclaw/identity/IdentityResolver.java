package io.jclaw.identity;

import java.util.Optional;

/**
 * Resolves a canonical user ID from a channel-specific user ID.
 * If no identity link exists, returns the channel user ID as-is.
 */
public class IdentityResolver {

    private final IdentityLinkStore store;

    public IdentityResolver(IdentityLinkStore store) {
        this.store = store;
    }

    /**
     * Resolve the canonical user ID for a given channel+user.
     * Returns the canonical ID if linked, or the channelUserId if not.
     */
    public String resolve(String channel, String channelUserId) {
        return store.resolveCanonicalId(channel, channelUserId)
                .orElse(channelUserId);
    }

    /**
     * Check if a channel user has a linked canonical identity.
     */
    public boolean isLinked(String channel, String channelUserId) {
        return store.resolveCanonicalId(channel, channelUserId).isPresent();
    }
}

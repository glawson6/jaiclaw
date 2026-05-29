package io.jaiclaw.channel.matrix;

import java.util.Set;

/**
 * Configuration for the Matrix channel adapter.
 *
 * @param homeserverUrl    Matrix homeserver URL (e.g. "https://matrix.org")
 * @param accessToken      access token for the bot user
 * @param userId           the bot's Matrix user ID (e.g. "@bot:matrix.org")
 * @param enabled          whether the adapter is enabled
 * @param syncTimeoutMs    timeout for long-poll sync requests in milliseconds
 * @param allowedSenderIds Matrix user IDs allowed to send messages (empty = allow all)
 */
public record MatrixConfig(
        String homeserverUrl,
        String accessToken,
        String userId,
        boolean enabled,
        int syncTimeoutMs,
        Set<String> allowedSenderIds
) {
    public MatrixConfig {
        if (homeserverUrl == null) homeserverUrl = "";
        if (accessToken == null) accessToken = "";
        if (userId == null) userId = "";
        if (syncTimeoutMs <= 0) syncTimeoutMs = 30000;
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    public boolean isSenderAllowed(String senderId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(senderId);
    }

    public static final MatrixConfig DISABLED = new MatrixConfig(
            "", "", "", false, 30000, Set.of()
    );

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String homeserverUrl;
        private String accessToken;
        private String userId;
        private boolean enabled;
        private int syncTimeoutMs;
        private Set<String> allowedSenderIds;

        public Builder homeserverUrl(String homeserverUrl) { this.homeserverUrl = homeserverUrl; return this; }
        public Builder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder syncTimeoutMs(int syncTimeoutMs) { this.syncTimeoutMs = syncTimeoutMs; return this; }
        public Builder allowedSenderIds(Set<String> allowedSenderIds) { this.allowedSenderIds = allowedSenderIds; return this; }

        public MatrixConfig build() {
            return new MatrixConfig(homeserverUrl, accessToken, userId, enabled, syncTimeoutMs, allowedSenderIds);
        }
    }
}

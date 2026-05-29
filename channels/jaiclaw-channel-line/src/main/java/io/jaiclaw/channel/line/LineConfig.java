package io.jaiclaw.channel.line;

import java.util.Set;

/**
 * Configuration for the LINE channel adapter.
 *
 * @param channelAccessToken LINE channel access token for outbound messaging
 * @param channelSecret      LINE channel secret for webhook signature verification
 * @param enabled            whether the adapter is enabled
 * @param allowedSenderIds   LINE user IDs allowed to send messages (empty = allow all)
 */
public record LineConfig(
        String channelAccessToken,
        String channelSecret,
        boolean enabled,
        Set<String> allowedSenderIds
) {
    public LineConfig {
        if (channelAccessToken == null) channelAccessToken = "";
        if (channelSecret == null) channelSecret = "";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    public boolean isSenderAllowed(String senderId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(senderId);
    }

    public static final LineConfig DISABLED = new LineConfig("", "", false, Set.of());

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String channelAccessToken;
        private String channelSecret;
        private boolean enabled;
        private Set<String> allowedSenderIds;

        public Builder channelAccessToken(String channelAccessToken) { this.channelAccessToken = channelAccessToken; return this; }
        public Builder channelSecret(String channelSecret) { this.channelSecret = channelSecret; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder allowedSenderIds(Set<String> allowedSenderIds) { this.allowedSenderIds = allowedSenderIds; return this; }

        public LineConfig build() {
            return new LineConfig(channelAccessToken, channelSecret, enabled, allowedSenderIds);
        }
    }
}

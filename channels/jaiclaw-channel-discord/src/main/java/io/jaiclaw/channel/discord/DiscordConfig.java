package io.jaiclaw.channel.discord;

import java.util.Set;

/**
 * Configuration for the Discord channel adapter.
 *
 * <p>Two inbound modes:
 * <ul>
 *   <li><b>Gateway WebSocket</b> (local dev): Set {@code useGateway} to true. No public endpoint needed.
 *   <li><b>Interactions webhook</b> (production): Leave {@code useGateway} false. Requires public endpoint.
 * </ul>
 *
 * <p>If {@code allowedSenderIds} is non-empty, only messages from those Discord user IDs
 * are processed; all others are silently dropped. An empty set means allow everyone.
 */
public record DiscordConfig(
        String botToken,
        String applicationId,
        boolean enabled,
        boolean useGateway,
        Set<String> allowedSenderIds
) {
    public DiscordConfig {
        if (botToken == null) botToken = "";
        if (applicationId == null) applicationId = "";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    /** Backwards-compatible 3-arg constructor (webhook mode). */
    public DiscordConfig(String botToken, String applicationId, boolean enabled) {
        this(botToken, applicationId, enabled, false, Set.of());
    }

    /** Backwards-compatible 4-arg constructor. */
    public DiscordConfig(String botToken, String applicationId, boolean enabled, boolean useGateway) {
        this(botToken, applicationId, enabled, useGateway, Set.of());
    }

    /**
     * Returns true if the given user ID is allowed to interact with the bot.
     * An empty allowedSenderIds set means all users are allowed.
     */
    public boolean isSenderAllowed(String userId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(userId);
    }

    public static final DiscordConfig DISABLED = new DiscordConfig("", "", false, false, Set.of());

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String botToken;
        private String applicationId;
        private boolean enabled;
        private boolean useGateway;
        private Set<String> allowedSenderIds;

        public Builder botToken(String botToken) { this.botToken = botToken; return this; }
        public Builder applicationId(String applicationId) { this.applicationId = applicationId; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder useGateway(boolean useGateway) { this.useGateway = useGateway; return this; }
        public Builder allowedSenderIds(Set<String> allowedSenderIds) { this.allowedSenderIds = allowedSenderIds; return this; }

        public DiscordConfig build() {
            return new DiscordConfig(botToken, applicationId, enabled, useGateway, allowedSenderIds);
        }
    }
}

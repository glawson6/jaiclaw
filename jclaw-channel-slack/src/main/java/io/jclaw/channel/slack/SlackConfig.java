package io.jclaw.channel.slack;

import java.util.Set;

/**
 * Configuration for the Slack channel adapter.
 *
 * <p>Two inbound modes:
 * <ul>
 *   <li><b>Socket Mode</b> (local dev): Set {@code appToken} (xapp-...). No public endpoint needed.
 *   <li><b>Events API webhook</b> (production): Leave {@code appToken} blank. Requires public endpoint.
 * </ul>
 *
 * <p>If {@code allowedSenderIds} is non-empty, only messages from those Slack user IDs
 * are processed; all others are silently dropped. An empty set means allow everyone.
 */
public record SlackConfig(
        String botToken,
        String signingSecret,
        boolean enabled,
        String appToken,
        Set<String> allowedSenderIds
) {
    public SlackConfig {
        if (botToken == null) botToken = "";
        if (signingSecret == null) signingSecret = "";
        if (appToken == null) appToken = "";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    /** Backwards-compatible 3-arg constructor (webhook mode). */
    public SlackConfig(String botToken, String signingSecret, boolean enabled) {
        this(botToken, signingSecret, enabled, "", Set.of());
    }

    /** Backwards-compatible 4-arg constructor. */
    public SlackConfig(String botToken, String signingSecret, boolean enabled, String appToken) {
        this(botToken, signingSecret, enabled, appToken, Set.of());
    }

    /** Use Socket Mode when appToken is present. */
    public boolean useSocketMode() {
        return !appToken.isBlank();
    }

    /**
     * Returns true if the given user ID is allowed to interact with the bot.
     * An empty allowedSenderIds set means all users are allowed.
     */
    public boolean isSenderAllowed(String userId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(userId);
    }

    public static final SlackConfig DISABLED = new SlackConfig("", "", false, "", Set.of());
}

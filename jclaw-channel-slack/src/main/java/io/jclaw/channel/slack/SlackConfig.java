package io.jclaw.channel.slack;

/**
 * Configuration for the Slack channel adapter.
 *
 * <p>Two inbound modes:
 * <ul>
 *   <li><b>Socket Mode</b> (local dev): Set {@code appToken} (xapp-...). No public endpoint needed.
 *   <li><b>Events API webhook</b> (production): Leave {@code appToken} blank. Requires public endpoint.
 * </ul>
 */
public record SlackConfig(
        String botToken,
        String signingSecret,
        boolean enabled,
        String appToken
) {
    public SlackConfig {
        if (botToken == null) botToken = "";
        if (signingSecret == null) signingSecret = "";
        if (appToken == null) appToken = "";
    }

    /** Backwards-compatible 3-arg constructor (webhook mode). */
    public SlackConfig(String botToken, String signingSecret, boolean enabled) {
        this(botToken, signingSecret, enabled, "");
    }

    /** Use Socket Mode when appToken is present. */
    public boolean useSocketMode() {
        return !appToken.isBlank();
    }

    public static final SlackConfig DISABLED = new SlackConfig("", "", false, "");
}

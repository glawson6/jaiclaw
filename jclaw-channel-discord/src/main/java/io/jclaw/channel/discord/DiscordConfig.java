package io.jclaw.channel.discord;

/**
 * Configuration for the Discord channel adapter.
 *
 * <p>Two inbound modes:
 * <ul>
 *   <li><b>Gateway WebSocket</b> (local dev): Set {@code useGateway} to true. No public endpoint needed.
 *   <li><b>Interactions webhook</b> (production): Leave {@code useGateway} false. Requires public endpoint.
 * </ul>
 */
public record DiscordConfig(
        String botToken,
        String applicationId,
        boolean enabled,
        boolean useGateway
) {
    public DiscordConfig {
        if (botToken == null) botToken = "";
        if (applicationId == null) applicationId = "";
    }

    /** Backwards-compatible 3-arg constructor (webhook mode). */
    public DiscordConfig(String botToken, String applicationId, boolean enabled) {
        this(botToken, applicationId, enabled, false);
    }

    public static final DiscordConfig DISABLED = new DiscordConfig("", "", false, false);
}

package io.jaiclaw.channel.teams;

import java.util.Set;

/**
 * Configuration for the Microsoft Teams channel adapter.
 *
 * <p>Inbound: Webhook-only — Teams sends HTTP POST with JWT-signed Activity payloads.
 *
 * <p>Outbound: Bot Framework REST API with Azure AD OAuth 2.0 bearer token.
 *
 * <p>If {@code allowedSenderIds} is non-empty, only messages from those AAD object IDs
 * are processed; all others are silently dropped. An empty set means allow everyone.
 */
public record TeamsConfig(
        String appId,
        String appSecret,
        boolean enabled,
        String tenantId,
        boolean skipJwtValidation,
        Set<String> allowedSenderIds
) {
    public TeamsConfig {
        if (appId == null) appId = "";
        if (appSecret == null) appSecret = "";
        if (tenantId == null) tenantId = "";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
    }

    /** Minimal constructor for testing. */
    public TeamsConfig(String appId, String appSecret, boolean enabled) {
        this(appId, appSecret, enabled, "", false, Set.of());
    }

    /**
     * Returns true if the given AAD object ID is allowed to interact with the bot.
     * An empty allowedSenderIds set means all users are allowed.
     */
    public boolean isSenderAllowed(String aadObjectId) {
        return allowedSenderIds.isEmpty() || allowedSenderIds.contains(aadObjectId);
    }

    public static final TeamsConfig DISABLED = new TeamsConfig("", "", false, "", false, Set.of());
}

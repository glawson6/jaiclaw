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
        Set<String> allowedSenderIds,
        String publicKey,
        boolean verifySignature
) {
    public DiscordConfig {
        if (botToken == null) botToken = "";
        if (applicationId == null) applicationId = "";
        if (allowedSenderIds == null) allowedSenderIds = Set.of();
        if (publicKey == null) publicKey = "";
    }

    /**
     * Backwards-compatible 5-arg constructor; signature verification off.
     *
     * <p>Hand-constructed record, not {@code @ConfigurationProperties}-bound, so
     * overloads are safe here — the Boot 4 one-constructor rule applies to the
     * binder-facing {@code ChannelsProperties.DiscordProperties} instead.
     */
    /**
     * Pre-1.3.0 shape, with interaction signature verification <strong>off</strong>
     * because this overload has nowhere to take the public key.
     *
     * @deprecated since 1.3.0 — webhook-mode deployments should supply
     *             {@code publicKey} and {@code verifySignature} via the canonical
     *             constructor or {@link #builder()}; the adapter warns at startup
     *             when verification is off.
     */
    @Deprecated(since = "1.3.0")
    public DiscordConfig(String botToken, String applicationId, boolean enabled,
                         boolean useGateway, Set<String> allowedSenderIds) {
        this(botToken, applicationId, enabled, useGateway, allowedSenderIds, "", false);
    }

    /**
     * True when inbound interaction signatures should be verified <em>and</em>
     * a key is available to verify them with.
     *
     * <p>Deliberately separate from {@link #verifySignature()} so the adapter can
     * tell "not asked for" apart from "asked for but unusable" and refuse to
     * start in the latter case, rather than silently accepting unsigned traffic
     * the way Slack and Telegram do on a blank secret.
     */
    public boolean signatureVerifiable() {
        return verifySignature && !publicKey.isBlank();
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

    public static final DiscordConfig DISABLED =
            new DiscordConfig("", "", false, false, Set.of(), "", false);

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String botToken;
        private String applicationId;
        private boolean enabled;
        private boolean useGateway;
        private Set<String> allowedSenderIds;
        private String publicKey;
        private boolean verifySignature;

        public Builder botToken(String botToken) { this.botToken = botToken; return this; }
        public Builder applicationId(String applicationId) { this.applicationId = applicationId; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder useGateway(boolean useGateway) { this.useGateway = useGateway; return this; }
        public Builder allowedSenderIds(Set<String> allowedSenderIds) { this.allowedSenderIds = allowedSenderIds; return this; }
        /** Discord application public key (hex) for interaction signature verification. */
        public Builder publicKey(String publicKey) { this.publicKey = publicKey; return this; }
        public Builder verifySignature(boolean verifySignature) { this.verifySignature = verifySignature; return this; }

        public DiscordConfig build() {
            return new DiscordConfig(botToken, applicationId, enabled, useGateway, allowedSenderIds,
                    publicKey, verifySignature);
        }
    }
}

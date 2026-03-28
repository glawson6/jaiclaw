package io.jaiclaw.config;

/**
 * Per-tenant channel credentials. Each inner record mirrors the existing
 * {@link ChannelsProperties} structure but is nullable — null means
 * "use application-level channel" or "not configured for this tenant."
 *
 * @param telegram Telegram bot credentials
 * @param slack    Slack bot credentials
 * @param discord  Discord bot credentials
 * @param sms      SMS (Twilio) credentials
 * @param email    Email IMAP/SMTP credentials
 * @param signal   Signal credentials
 * @param teams    Microsoft Teams credentials
 */
public record TenantChannelsConfig(
        TelegramChannelConfig telegram,
        SlackChannelConfig slack,
        DiscordChannelConfig discord,
        SmsChannelConfig sms,
        EmailChannelConfig email,
        SignalChannelConfig signal,
        TeamsChannelConfig teams
) {
    public static final TenantChannelsConfig EMPTY = new TenantChannelsConfig(
            null, null, null, null, null, null, null
    );

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TelegramChannelConfig telegram;
        private SlackChannelConfig slack;
        private DiscordChannelConfig discord;
        private SmsChannelConfig sms;
        private EmailChannelConfig email;
        private SignalChannelConfig signal;
        private TeamsChannelConfig teams;

        public Builder telegram(TelegramChannelConfig telegram) { this.telegram = telegram; return this; }
        public Builder slack(SlackChannelConfig slack) { this.slack = slack; return this; }
        public Builder discord(DiscordChannelConfig discord) { this.discord = discord; return this; }
        public Builder sms(SmsChannelConfig sms) { this.sms = sms; return this; }
        public Builder email(EmailChannelConfig email) { this.email = email; return this; }
        public Builder signal(SignalChannelConfig signal) { this.signal = signal; return this; }
        public Builder teams(TeamsChannelConfig teams) { this.teams = teams; return this; }

        public TenantChannelsConfig build() {
            return new TenantChannelsConfig(telegram, slack, discord, sms, email, signal, teams);
        }
    }

    public record TelegramChannelConfig(
            String botToken,
            String webhookUrl,
            String allowedUsers,
            boolean enabled
    ) {}

    public record SlackChannelConfig(
            String botToken,
            String signingSecret,
            String appToken,
            String allowedSenders,
            boolean enabled
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String botToken;
            private String signingSecret;
            private String appToken;
            private String allowedSenders;
            private boolean enabled;

            public Builder botToken(String botToken) { this.botToken = botToken; return this; }
            public Builder signingSecret(String signingSecret) { this.signingSecret = signingSecret; return this; }
            public Builder appToken(String appToken) { this.appToken = appToken; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }
            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

            public SlackChannelConfig build() {
                return new SlackChannelConfig(botToken, signingSecret, appToken, allowedSenders, enabled);
            }
        }
    }

    public record DiscordChannelConfig(
            String botToken,
            String applicationId,
            boolean enabled
    ) {}

    public record SmsChannelConfig(
            String accountSid,
            String authToken,
            String fromNumber,
            boolean enabled
    ) {}

    public record EmailChannelConfig(
            String imapHost,
            int imapPort,
            String smtpHost,
            int smtpPort,
            String username,
            String password,
            boolean enabled
    ) {
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String imapHost;
            private int imapPort;
            private String smtpHost;
            private int smtpPort;
            private String username;
            private String password;
            private boolean enabled;

            public Builder imapHost(String imapHost) { this.imapHost = imapHost; return this; }
            public Builder imapPort(int imapPort) { this.imapPort = imapPort; return this; }
            public Builder smtpHost(String smtpHost) { this.smtpHost = smtpHost; return this; }
            public Builder smtpPort(int smtpPort) { this.smtpPort = smtpPort; return this; }
            public Builder username(String username) { this.username = username; return this; }
            public Builder password(String password) { this.password = password; return this; }
            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

            public EmailChannelConfig build() {
                return new EmailChannelConfig(imapHost, imapPort, smtpHost, smtpPort, username, password, enabled);
            }
        }
    }

    public record SignalChannelConfig(
            String phoneNumber,
            String apiUrl,
            boolean enabled
    ) {}

    public record TeamsChannelConfig(
            String appId,
            String appSecret,
            String tenantId,
            boolean enabled
    ) {}
}

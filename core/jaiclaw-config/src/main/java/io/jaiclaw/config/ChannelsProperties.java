package io.jaiclaw.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record ChannelsProperties(
        TelegramProperties telegram,
        EmailProperties email,
        SmsProperties sms,
        SlackProperties slack,
        DiscordProperties discord,
        SignalProperties signal,
        TeamsProperties teams
) {
    public static final ChannelsProperties DEFAULT = new ChannelsProperties(
            TelegramProperties.DEFAULT,
            EmailProperties.DEFAULT,
            SmsProperties.DEFAULT,
            SlackProperties.DEFAULT,
            DiscordProperties.DEFAULT,
            SignalProperties.DEFAULT,
            TeamsProperties.DEFAULT
    );

    public ChannelsProperties {
        if (telegram == null) telegram = TelegramProperties.DEFAULT;
        if (email == null) email = EmailProperties.DEFAULT;
        if (sms == null) sms = SmsProperties.DEFAULT;
        if (slack == null) slack = SlackProperties.DEFAULT;
        if (discord == null) discord = DiscordProperties.DEFAULT;
        if (signal == null) signal = SignalProperties.DEFAULT;
        if (teams == null) teams = TeamsProperties.DEFAULT;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private TelegramProperties telegram;
        private EmailProperties email;
        private SmsProperties sms;
        private SlackProperties slack;
        private DiscordProperties discord;
        private SignalProperties signal;
        private TeamsProperties teams;

        public Builder telegram(TelegramProperties telegram) { this.telegram = telegram; return this; }
        public Builder email(EmailProperties email) { this.email = email; return this; }
        public Builder sms(SmsProperties sms) { this.sms = sms; return this; }
        public Builder slack(SlackProperties slack) { this.slack = slack; return this; }
        public Builder discord(DiscordProperties discord) { this.discord = discord; return this; }
        public Builder signal(SignalProperties signal) { this.signal = signal; return this; }
        public Builder teams(TeamsProperties teams) { this.teams = teams; return this; }

        public ChannelsProperties build() {
            return new ChannelsProperties(telegram, email, sms, slack, discord, signal, teams);
        }
    }

    public record TelegramProperties(
            boolean enabled,
            String botToken,
            String webhookUrl,
            String allowedUsers,
            int pollingTimeoutSeconds,
            boolean verifyWebhook,
            String webhookSecretToken,
            boolean maskBotToken
    ) {
        public static final TelegramProperties DEFAULT = new TelegramProperties(
                false, null, null, null, 30, false, null, false
        );

        public Set<String> allowedUserIds() {
            if (allowedUsers == null || allowedUsers.isBlank()) return Set.of();
            return Arrays.stream(allowedUsers.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String botToken;
            private String webhookUrl;
            private String allowedUsers;
            private int pollingTimeoutSeconds;
            private boolean verifyWebhook;
            private String webhookSecretToken;
            private boolean maskBotToken;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder botToken(String botToken) { this.botToken = botToken; return this; }
            public Builder webhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; return this; }
            public Builder allowedUsers(String allowedUsers) { this.allowedUsers = allowedUsers; return this; }
            public Builder pollingTimeoutSeconds(int pollingTimeoutSeconds) { this.pollingTimeoutSeconds = pollingTimeoutSeconds; return this; }
            public Builder verifyWebhook(boolean verifyWebhook) { this.verifyWebhook = verifyWebhook; return this; }
            public Builder webhookSecretToken(String webhookSecretToken) { this.webhookSecretToken = webhookSecretToken; return this; }
            public Builder maskBotToken(boolean maskBotToken) { this.maskBotToken = maskBotToken; return this; }

            public TelegramProperties build() {
                return new TelegramProperties(enabled, botToken, webhookUrl, allowedUsers,
                        pollingTimeoutSeconds, verifyWebhook, webhookSecretToken, maskBotToken);
            }
        }
    }

    public record EmailProperties(
            boolean enabled,
            String provider,
            String imapHost,
            int imapPort,
            String smtpHost,
            int smtpPort,
            String username,
            String password,
            int pollInterval,
            String allowedSenders
    ) {
        public static final EmailProperties DEFAULT = new EmailProperties(
                false, "imap", null, 993, null, 587, null, null, 60, null
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String provider;
            private String imapHost;
            private int imapPort;
            private String smtpHost;
            private int smtpPort;
            private String username;
            private String password;
            private int pollInterval;
            private String allowedSenders;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder imapHost(String imapHost) { this.imapHost = imapHost; return this; }
            public Builder imapPort(int imapPort) { this.imapPort = imapPort; return this; }
            public Builder smtpHost(String smtpHost) { this.smtpHost = smtpHost; return this; }
            public Builder smtpPort(int smtpPort) { this.smtpPort = smtpPort; return this; }
            public Builder username(String username) { this.username = username; return this; }
            public Builder password(String password) { this.password = password; return this; }
            public Builder pollInterval(int pollInterval) { this.pollInterval = pollInterval; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }

            public EmailProperties build() {
                return new EmailProperties(enabled, provider, imapHost, imapPort, smtpHost,
                        smtpPort, username, password, pollInterval, allowedSenders);
            }
        }
    }

    public record SmsProperties(
            boolean enabled,
            String accountSid,
            String authToken,
            String fromNumber,
            String webhookPath,
            String allowedSenders
    ) {
        public static final SmsProperties DEFAULT = new SmsProperties(
                false, null, null, null, "/webhooks/sms", null
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String accountSid;
            private String authToken;
            private String fromNumber;
            private String webhookPath;
            private String allowedSenders;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder accountSid(String accountSid) { this.accountSid = accountSid; return this; }
            public Builder authToken(String authToken) { this.authToken = authToken; return this; }
            public Builder fromNumber(String fromNumber) { this.fromNumber = fromNumber; return this; }
            public Builder webhookPath(String webhookPath) { this.webhookPath = webhookPath; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }

            public SmsProperties build() {
                return new SmsProperties(enabled, accountSid, authToken, fromNumber, webhookPath, allowedSenders);
            }
        }
    }

    public record SlackProperties(
            boolean enabled,
            String botToken,
            String signingSecret,
            String appToken,
            String allowedSenders,
            boolean verifySignature
    ) {
        public static final SlackProperties DEFAULT = new SlackProperties(
                false, null, null, null, null, false
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String botToken;
            private String signingSecret;
            private String appToken;
            private String allowedSenders;
            private boolean verifySignature;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder botToken(String botToken) { this.botToken = botToken; return this; }
            public Builder signingSecret(String signingSecret) { this.signingSecret = signingSecret; return this; }
            public Builder appToken(String appToken) { this.appToken = appToken; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }
            public Builder verifySignature(boolean verifySignature) { this.verifySignature = verifySignature; return this; }

            public SlackProperties build() {
                return new SlackProperties(enabled, botToken, signingSecret, appToken, allowedSenders, verifySignature);
            }
        }
    }

    public record DiscordProperties(
            boolean enabled,
            String botToken,
            String applicationId,
            boolean useGateway,
            String allowedSenders
    ) {
        public static final DiscordProperties DEFAULT = new DiscordProperties(
                false, null, null, false, null
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String botToken;
            private String applicationId;
            private boolean useGateway;
            private String allowedSenders;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder botToken(String botToken) { this.botToken = botToken; return this; }
            public Builder applicationId(String applicationId) { this.applicationId = applicationId; return this; }
            public Builder useGateway(boolean useGateway) { this.useGateway = useGateway; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }

            public DiscordProperties build() {
                return new DiscordProperties(enabled, botToken, applicationId, useGateway, allowedSenders);
            }
        }
    }

    public record SignalProperties(
            boolean enabled,
            String mode,
            String apiUrl,
            String phoneNumber,
            int pollIntervalSeconds,
            String cliCommand,
            int tcpPort,
            String allowedSenders
    ) {
        public static final SignalProperties DEFAULT = new SignalProperties(
                false, "http-client", null, null, 2, "signal-cli", 7583, null
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String mode;
            private String apiUrl;
            private String phoneNumber;
            private int pollIntervalSeconds;
            private String cliCommand;
            private int tcpPort;
            private String allowedSenders;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder mode(String mode) { this.mode = mode; return this; }
            public Builder apiUrl(String apiUrl) { this.apiUrl = apiUrl; return this; }
            public Builder phoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }
            public Builder pollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; return this; }
            public Builder cliCommand(String cliCommand) { this.cliCommand = cliCommand; return this; }
            public Builder tcpPort(int tcpPort) { this.tcpPort = tcpPort; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }

            public SignalProperties build() {
                return new SignalProperties(enabled, mode, apiUrl, phoneNumber, pollIntervalSeconds,
                        cliCommand, tcpPort, allowedSenders);
            }
        }
    }

    public record TeamsProperties(
            boolean enabled,
            String appId,
            String appSecret,
            String tenantId,
            boolean skipJwtValidation,
            String allowedSenders
    ) {
        public static final TeamsProperties DEFAULT = new TeamsProperties(
                false, null, null, null, false, null
        );

        public Set<String> allowedSenderIds() {
            if (allowedSenders == null || allowedSenders.isBlank()) return Set.of();
            return Arrays.stream(allowedSenders.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private boolean enabled;
            private String appId;
            private String appSecret;
            private String tenantId;
            private boolean skipJwtValidation;
            private String allowedSenders;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder appId(String appId) { this.appId = appId; return this; }
            public Builder appSecret(String appSecret) { this.appSecret = appSecret; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder skipJwtValidation(boolean skipJwtValidation) { this.skipJwtValidation = skipJwtValidation; return this; }
            public Builder allowedSenders(String allowedSenders) { this.allowedSenders = allowedSenders; return this; }

            public TeamsProperties build() {
                return new TeamsProperties(enabled, appId, appSecret, tenantId, skipJwtValidation, allowedSenders);
            }
        }
    }
}

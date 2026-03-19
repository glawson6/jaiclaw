package io.jclaw.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record ChannelsProperties(
        TelegramProperties telegram,
        EmailProperties email,
        SmsProperties sms,
        SlackProperties slack,
        DiscordProperties discord
) {
    public static final ChannelsProperties DEFAULT = new ChannelsProperties(
            TelegramProperties.DEFAULT,
            EmailProperties.DEFAULT,
            SmsProperties.DEFAULT,
            SlackProperties.DEFAULT,
            DiscordProperties.DEFAULT
    );

    public ChannelsProperties {
        if (telegram == null) telegram = TelegramProperties.DEFAULT;
        if (email == null) email = EmailProperties.DEFAULT;
        if (sms == null) sms = SmsProperties.DEFAULT;
        if (slack == null) slack = SlackProperties.DEFAULT;
        if (discord == null) discord = DiscordProperties.DEFAULT;
    }

    public record TelegramProperties(
            boolean enabled,
            String botToken,
            String webhookUrl,
            String allowedUsers,
            int pollingTimeoutSeconds
    ) {
        public static final TelegramProperties DEFAULT = new TelegramProperties(
                false, null, null, null, 30
        );

        public Set<String> allowedUserIds() {
            if (allowedUsers == null || allowedUsers.isBlank()) return Set.of();
            return Arrays.stream(allowedUsers.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
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
            int pollInterval
    ) {
        public static final EmailProperties DEFAULT = new EmailProperties(
                false, "imap", null, 993, null, 587, null, null, 60
        );
    }

    public record SmsProperties(
            boolean enabled,
            String accountSid,
            String authToken,
            String fromNumber,
            String webhookPath
    ) {
        public static final SmsProperties DEFAULT = new SmsProperties(
                false, null, null, null, "/webhooks/sms"
        );
    }

    public record SlackProperties(
            boolean enabled,
            String botToken,
            String signingSecret,
            String appToken
    ) {
        public static final SlackProperties DEFAULT = new SlackProperties(
                false, null, null, null
        );
    }

    public record DiscordProperties(
            boolean enabled,
            String botToken,
            String applicationId,
            boolean useGateway
    ) {
        public static final DiscordProperties DEFAULT = new DiscordProperties(
                false, null, null, false
        );
    }
}

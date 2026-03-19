package io.jclaw.autoconfigure;

import io.jclaw.config.JClawProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Channel adapter auto-configuration — runs after {@link JClawGatewayAutoConfiguration}
 * so that {@code WebhookDispatcher} is available for channel adapters that need it.
 */
@AutoConfiguration
@AutoConfigureAfter(JClawGatewayAutoConfiguration.class)
public class JClawChannelAutoConfiguration {

    /**
     * Email adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.email.EmailAdapter")
    static class EmailAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.email.EmailAdapter emailAdapter() {
            var config = new io.jclaw.channel.email.EmailConfig(
                    System.getenv("EMAIL_PROVIDER"),
                    System.getenv("EMAIL_IMAP_HOST"),
                    parseIntEnv("EMAIL_IMAP_PORT", 993),
                    System.getenv("EMAIL_SMTP_HOST"),
                    parseIntEnv("EMAIL_SMTP_PORT", 587),
                    System.getenv("EMAIL_USERNAME"),
                    System.getenv("EMAIL_PASSWORD"),
                    System.getenv("EMAIL_USERNAME") != null,
                    parseIntEnv("EMAIL_POLL_INTERVAL", 60),
                    null);
            return new io.jclaw.channel.email.EmailAdapter(config);
        }

        private static int parseIntEnv(String key, int defaultValue) {
            String val = System.getenv(key);
            if (val == null || val.isBlank()) return defaultValue;
            try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
        }
    }

    /**
     * SMS adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.sms.SmsAdapter")
    static class SmsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.sms.SmsAdapter smsAdapter() {
            var config = new io.jclaw.channel.sms.SmsConfig(
                    System.getenv("TWILIO_ACCOUNT_SID"),
                    System.getenv("TWILIO_AUTH_TOKEN"),
                    System.getenv("TWILIO_FROM_NUMBER"),
                    null,
                    System.getenv("TWILIO_ACCOUNT_SID") != null);
            return new io.jclaw.channel.sms.SmsAdapter(config);
        }
    }

    /**
     * Audit auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.audit.AuditLogger")
    static class AuditAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(type = "io.jclaw.audit.AuditLogger")
        public io.jclaw.audit.InMemoryAuditLogger inMemoryAuditLogger() {
            return new io.jclaw.audit.InMemoryAuditLogger();
        }
    }

    /**
     * Telegram adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.telegram.TelegramAdapter")
    static class TelegramAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.telegram.TelegramAdapter telegramAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            Set<String> allowedUsers = parseCommaSeparated(System.getenv("TELEGRAM_ALLOWED_USERS"));
            var config = new io.jclaw.channel.telegram.TelegramConfig(
                    System.getenv("TELEGRAM_BOT_TOKEN"),
                    System.getenv("TELEGRAM_WEBHOOK_URL"),
                    System.getenv("TELEGRAM_BOT_TOKEN") != null,
                    30,
                    allowedUsers);
            return new io.jclaw.channel.telegram.TelegramAdapter(config, webhookDispatcher);
        }

        private static Set<String> parseCommaSeparated(String value) {
            if (value == null || value.isBlank()) return Set.of();
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * Slack adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.slack.SlackAdapter")
    static class SlackAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.slack.SlackAdapter slackAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var config = new io.jclaw.channel.slack.SlackConfig(
                    System.getenv("SLACK_BOT_TOKEN"),
                    System.getenv("SLACK_SIGNING_SECRET"),
                    System.getenv("SLACK_BOT_TOKEN") != null,
                    System.getenv("SLACK_APP_TOKEN"));
            return new io.jclaw.channel.slack.SlackAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Discord adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.discord.DiscordAdapter")
    static class DiscordAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.discord.DiscordAdapter discordAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            String useGateway = System.getenv("DISCORD_USE_GATEWAY");
            var config = new io.jclaw.channel.discord.DiscordConfig(
                    System.getenv("DISCORD_BOT_TOKEN"),
                    System.getenv("DISCORD_APPLICATION_ID"),
                    System.getenv("DISCORD_BOT_TOKEN") != null,
                    "true".equalsIgnoreCase(useGateway));
            return new io.jclaw.channel.discord.DiscordAdapter(config, webhookDispatcher);
        }
    }
}

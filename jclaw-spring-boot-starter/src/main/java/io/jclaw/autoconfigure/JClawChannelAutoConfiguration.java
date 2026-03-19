package io.jclaw.autoconfigure;

import io.jclaw.config.ChannelsProperties;
import io.jclaw.config.JClawProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Channel adapter auto-configuration — runs after {@link JClawGatewayAutoConfiguration}
 * so that {@code WebhookDispatcher} is available for channel adapters that need it.
 *
 * <p>Channel configuration is resolved from {@link ChannelsProperties} (bound to
 * {@code jclaw.channels.*}), which participates in Spring's full property resolution
 * (system properties, env vars, application.yml, etc.).
 *
 * <p>Each adapter is gated on its explicit {@code enabled} property
 * ({@code jclaw.channels.<channel>.enabled=true}), so unconfigured channels are
 * silently skipped.
 */
@AutoConfiguration
@AutoConfigureAfter(JClawGatewayAutoConfiguration.class)
public class JClawChannelAutoConfiguration {

    /**
     * Email adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.email.EmailAdapter")
    @ConditionalOnProperty(prefix = "jclaw.channels.email", name = "enabled", havingValue = "true")
    static class EmailAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.email.EmailAdapter emailAdapter(JClawProperties properties) {
            var email = properties.channels().email();
            var config = new io.jclaw.channel.email.EmailConfig(
                    email.provider(),
                    email.imapHost(),
                    email.imapPort(),
                    email.smtpHost(),
                    email.smtpPort(),
                    email.username(),
                    email.password(),
                    email.enabled(),
                    email.pollInterval(),
                    null);
            return new io.jclaw.channel.email.EmailAdapter(config);
        }
    }

    /**
     * SMS adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.sms.SmsAdapter")
    @ConditionalOnProperty(prefix = "jclaw.channels.sms", name = "enabled", havingValue = "true")
    static class SmsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jclaw.channel.sms.SmsAdapter smsAdapter(JClawProperties properties) {
            var sms = properties.channels().sms();
            var config = new io.jclaw.channel.sms.SmsConfig(
                    sms.accountSid(),
                    sms.authToken(),
                    sms.fromNumber(),
                    sms.webhookPath(),
                    sms.enabled());
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
    @ConditionalOnProperty(prefix = "jclaw.channels.telegram", name = "enabled", havingValue = "true")
    static class TelegramAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.telegram.TelegramAdapter telegramAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var telegram = properties.channels().telegram();
            var config = new io.jclaw.channel.telegram.TelegramConfig(
                    telegram.botToken(),
                    telegram.webhookUrl(),
                    telegram.enabled(),
                    telegram.pollingTimeoutSeconds(),
                    telegram.allowedUserIds());
            return new io.jclaw.channel.telegram.TelegramAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Slack adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.slack.SlackAdapter")
    @ConditionalOnProperty(prefix = "jclaw.channels.slack", name = "enabled", havingValue = "true")
    static class SlackAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.slack.SlackAdapter slackAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var slack = properties.channels().slack();
            var config = new io.jclaw.channel.slack.SlackConfig(
                    slack.botToken(),
                    slack.signingSecret(),
                    slack.enabled(),
                    slack.appToken());
            return new io.jclaw.channel.slack.SlackAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Discord adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jclaw.channel.discord.DiscordAdapter")
    @ConditionalOnProperty(prefix = "jclaw.channels.discord", name = "enabled", havingValue = "true")
    static class DiscordAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jclaw.gateway.WebhookDispatcher.class)
        public io.jclaw.channel.discord.DiscordAdapter discordAdapter(
                JClawProperties properties,
                io.jclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var discord = properties.channels().discord();
            var config = new io.jclaw.channel.discord.DiscordConfig(
                    discord.botToken(),
                    discord.applicationId(),
                    discord.enabled(),
                    discord.useGateway());
            return new io.jclaw.channel.discord.DiscordAdapter(config, webhookDispatcher);
        }
    }
}

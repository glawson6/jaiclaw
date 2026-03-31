package io.jaiclaw.autoconfigure;

import io.jaiclaw.config.ChannelsProperties;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.TenantAgentConfig;
import io.jaiclaw.config.TenantAgentConfigService;
import io.jaiclaw.config.TenantChannelsConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Channel adapter auto-configuration — runs after {@link JaiClawGatewayAutoConfiguration}
 * so that {@code WebhookDispatcher} is available for channel adapters that need it.
 *
 * <p>Channel configuration is resolved from {@link ChannelsProperties} (bound to
 * {@code jaiclaw.channels.*}), which participates in Spring's full property resolution
 * (system properties, env vars, application.yml, etc.).
 *
 * <p>Each adapter is gated on its explicit {@code enabled} property
 * ({@code jaiclaw.channels.<channel>.enabled=true}), so unconfigured channels are
 * silently skipped.
 */
@AutoConfiguration
@AutoConfigureAfter(JaiClawGatewayAutoConfiguration.class)
public class JaiClawChannelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawChannelAutoConfiguration.class);

    /**
     * Startup hook that scans and pre-loads tenant configs in MULTI mode,
     * registers bot-token-to-tenant mappings, and starts per-tenant channel adapters.
     */
    @Bean
    @ConditionalOnBean({TenantAgentConfigService.class})
    public ApplicationRunner tenantConfigStartupHook(
            TenantAgentConfigService configService,
            ObjectProvider<io.jaiclaw.gateway.tenant.BotTokenTenantResolver> botTokenResolverProvider) {
        return args -> {
            // Scan and load all tenant configs
            configService.scanAndLoadAll();

            // Auto-register bot token → tenant mappings
            var botTokenResolver = botTokenResolverProvider.getIfAvailable();
            if (botTokenResolver != null) {
                for (var entry : configService.allConfigurations().entrySet()) {
                    String tenantId = entry.getKey();
                    TenantAgentConfig config = entry.getValue();
                    TenantChannelsConfig channels = config.channels();
                    if (channels == null) continue;

                    if (channels.telegram() != null && channels.telegram().botToken() != null) {
                        botTokenResolver.register(
                                channels.telegram().botToken(), tenantId, config.name());
                    }
                    if (channels.slack() != null && channels.slack().botToken() != null) {
                        botTokenResolver.register(
                                channels.slack().botToken(), tenantId, config.name());
                    }
                }
                log.info("Registered {} bot-token-to-tenant mappings", botTokenResolver.mappingCount());
            }
        };
    }

    /**
     * Email adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.email.EmailAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.email", name = "enabled", havingValue = "true")
    static class EmailAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.email.EmailAdapter emailAdapter(JaiClawProperties properties) {
            var email = properties.channels().email();
            var config = new io.jaiclaw.channel.email.EmailConfig(
                    email.provider(),
                    email.imapHost(),
                    email.imapPort(),
                    email.smtpHost(),
                    email.smtpPort(),
                    email.username(),
                    email.password(),
                    email.enabled(),
                    email.pollInterval(),
                    null,
                    email.allowedSenderIds());
            return new io.jaiclaw.channel.email.EmailAdapter(config);
        }
    }

    /**
     * SMS adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.sms.SmsAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.sms", name = "enabled", havingValue = "true")
    static class SmsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.sms.SmsAdapter smsAdapter(JaiClawProperties properties) {
            var sms = properties.channels().sms();
            var config = new io.jaiclaw.channel.sms.SmsConfig(
                    sms.accountSid(),
                    sms.authToken(),
                    sms.fromNumber(),
                    sms.webhookPath(),
                    sms.enabled(),
                    sms.allowedSenderIds());
            return new io.jaiclaw.channel.sms.SmsAdapter(config);
        }
    }

    /**
     * Audit auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.audit.AuditLogger")
    static class AuditAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(type = "io.jaiclaw.audit.AuditLogger")
        public io.jaiclaw.audit.InMemoryAuditLogger inMemoryAuditLogger(
                io.jaiclaw.core.tenant.TenantGuard tenantGuard) {
            return new io.jaiclaw.audit.InMemoryAuditLogger(tenantGuard);
        }
    }

    /**
     * Telegram adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.telegram.TelegramAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.telegram", name = "enabled", havingValue = "true")
    static class TelegramAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.gateway.WebhookDispatcher.class)
        public io.jaiclaw.channel.telegram.TelegramAdapter telegramAdapter(
                JaiClawProperties properties,
                io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var telegram = properties.channels().telegram();
            var config = new io.jaiclaw.channel.telegram.TelegramConfig(
                    telegram.botToken(),
                    telegram.webhookUrl(),
                    telegram.enabled(),
                    telegram.pollingTimeoutSeconds(),
                    telegram.allowedUserIds(),
                    telegram.verifyWebhook(),
                    telegram.webhookSecretToken(),
                    telegram.maskBotToken());
            return new io.jaiclaw.channel.telegram.TelegramAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Slack adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.slack.SlackAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.slack", name = "enabled", havingValue = "true")
    static class SlackAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.gateway.WebhookDispatcher.class)
        public io.jaiclaw.channel.slack.SlackAdapter slackAdapter(
                JaiClawProperties properties,
                io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var slack = properties.channels().slack();
            var config = new io.jaiclaw.channel.slack.SlackConfig(
                    slack.botToken(),
                    slack.signingSecret(),
                    slack.enabled(),
                    slack.appToken(),
                    slack.allowedSenderIds(),
                    slack.verifySignature());
            return new io.jaiclaw.channel.slack.SlackAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Discord adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.discord.DiscordAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.discord", name = "enabled", havingValue = "true")
    static class DiscordAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.gateway.WebhookDispatcher.class)
        public io.jaiclaw.channel.discord.DiscordAdapter discordAdapter(
                JaiClawProperties properties,
                io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var discord = properties.channels().discord();
            var config = new io.jaiclaw.channel.discord.DiscordConfig(
                    discord.botToken(),
                    discord.applicationId(),
                    discord.enabled(),
                    discord.useGateway(),
                    discord.allowedSenderIds());
            return new io.jaiclaw.channel.discord.DiscordAdapter(config, webhookDispatcher);
        }
    }

    /**
     * Signal adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.signal.SignalAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.signal", name = "enabled", havingValue = "true")
    static class SignalAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.signal.SignalAdapter signalAdapter(JaiClawProperties properties) {
            var signal = properties.channels().signal();
            var mode = "embedded".equalsIgnoreCase(signal.mode())
                    ? io.jaiclaw.channel.signal.SignalMode.EMBEDDED
                    : io.jaiclaw.channel.signal.SignalMode.HTTP_CLIENT;
            var config = new io.jaiclaw.channel.signal.SignalConfig(
                    mode,
                    signal.phoneNumber(),
                    signal.enabled(),
                    signal.apiUrl(),
                    signal.pollIntervalSeconds(),
                    signal.cliCommand(),
                    signal.tcpPort(),
                    signal.allowedSenderIds());
            return new io.jaiclaw.channel.signal.SignalAdapter(config);
        }
    }

    /**
     * Microsoft Teams adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.teams.TeamsAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.teams", name = "enabled", havingValue = "true")
    static class TeamsAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.gateway.WebhookDispatcher.class)
        public io.jaiclaw.channel.teams.TeamsAdapter teamsAdapter(
                JaiClawProperties properties,
                io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher) {
            var teams = properties.channels().teams();
            var config = new io.jaiclaw.channel.teams.TeamsConfig(
                    teams.appId(),
                    teams.appSecret(),
                    teams.enabled(),
                    teams.tenantId(),
                    teams.skipJwtValidation(),
                    teams.allowedSenderIds());
            return new io.jaiclaw.channel.teams.TeamsAdapter(config, webhookDispatcher);
        }
    }
}

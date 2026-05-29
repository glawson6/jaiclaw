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

        /**
         * Camel-based polling strategy — activated when Camel is on the classpath
         * and {@code jaiclaw.channels.telegram.polling-strategy=camel}.
         */
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.apache.camel.CamelContext")
        @ConditionalOnProperty(prefix = "jaiclaw.channels.telegram", name = "polling-strategy", havingValue = "camel")
        static class CamelPollingAutoConfiguration {

            @Bean
            @ConditionalOnMissingBean(io.jaiclaw.channel.telegram.TelegramPollingStrategy.class)
            public io.jaiclaw.channel.telegram.TelegramPollingStrategy camelTelegramPollingStrategy(
                    org.apache.camel.CamelContext camelContext) {
                log.info("Creating CamelTelegramPollingStrategy");
                return new io.jaiclaw.channel.telegram.CamelTelegramPollingStrategy(camelContext);
            }
        }

        @Bean
        @ConditionalOnMissingBean(io.jaiclaw.channel.telegram.TelegramHttpClient.class)
        public io.jaiclaw.channel.telegram.TelegramHttpClient telegramHttpClient(
                JaiClawProperties properties) {
            var telegram = properties.channels().telegram();
            int timeout = telegram.pollingTimeoutSeconds();
            var clientType = io.jaiclaw.channel.telegram.TelegramHttpClientType
                    .fromString(telegram.httpClient());
            log.info("Creating TelegramHttpClient: {} (timeout={}s)", clientType, timeout);
            return switch (clientType) {
                case JDK -> new io.jaiclaw.channel.telegram.JdkHttpClientTelegramHttpClient(timeout);
                case REST_TEMPLATE -> new io.jaiclaw.channel.telegram.RestTemplateTelegramHttpClient(timeout);
                case WEB_CLIENT -> new io.jaiclaw.channel.telegram.WebClientTelegramHttpClient(timeout);
            };
        }

        @Bean
        @ConditionalOnMissingBean(io.jaiclaw.channel.telegram.TelegramPollingStrategy.class)
        public io.jaiclaw.channel.telegram.TelegramPollingStrategy telegramPollingStrategy(
                JaiClawProperties properties,
                io.jaiclaw.channel.telegram.TelegramHttpClient httpClient) {
            log.info("Creating NativeTelegramPollingStrategy");
            return new io.jaiclaw.channel.telegram.NativeTelegramPollingStrategy(httpClient);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.jaiclaw.gateway.WebhookDispatcher.class)
        public io.jaiclaw.channel.telegram.TelegramAdapter telegramAdapter(
                JaiClawProperties properties,
                io.jaiclaw.gateway.WebhookDispatcher webhookDispatcher,
                io.jaiclaw.channel.telegram.TelegramHttpClient httpClient,
                io.jaiclaw.channel.telegram.TelegramPollingStrategy pollingStrategy) {
            var telegram = properties.channels().telegram();
            var strategyType = io.jaiclaw.channel.telegram.TelegramPollingStrategyType
                    .fromString(telegram.pollingStrategy());
            var config = new io.jaiclaw.channel.telegram.TelegramConfig(
                    telegram.botToken(),
                    telegram.webhookUrl(),
                    telegram.enabled(),
                    telegram.pollingTimeoutSeconds(),
                    telegram.allowedUserIds(),
                    telegram.verifyWebhook(),
                    telegram.webhookSecretToken(),
                    telegram.maskBotToken(),
                    strategyType);
            return new io.jaiclaw.channel.telegram.TelegramAdapter(
                    config, webhookDispatcher, httpClient, pollingStrategy);
        }

        @Bean
        @ConditionalOnMissingBean(io.jaiclaw.security.ratelimit.UserRateLimiter.class)
        @ConditionalOnClass(name = "io.jaiclaw.security.ratelimit.UserRateLimiter")
        public io.jaiclaw.security.ratelimit.UserRateLimiter telegramUserRateLimiter(
                JaiClawProperties properties) {
            int rateLimit = properties.channels().telegram().rateLimit();
            log.info("Creating UserRateLimiter with {} messages/minute for Telegram", rateLimit);
            return new io.jaiclaw.security.ratelimit.UserRateLimiter(rateLimit);
        }

        @Bean
        @ConditionalOnMissingBean(io.jaiclaw.channel.telegram.TelegramUserIdFilter.class)
        @ConditionalOnClass(name = "io.jaiclaw.channel.telegram.TelegramUserIdFilter")
        @ConditionalOnBean(io.jaiclaw.security.ratelimit.UserRateLimiter.class)
        public io.jaiclaw.channel.telegram.TelegramUserIdFilter telegramUserIdFilter(
                JaiClawProperties properties,
                io.jaiclaw.security.ratelimit.UserRateLimiter rateLimiter,
                io.jaiclaw.gateway.GatewayService gatewayService) {
            var allowedUsers = properties.channels().telegram().allowedUserIds();
            log.info("Creating TelegramUserIdFilter with {} allowed users", allowedUsers.size());
            var filter = new io.jaiclaw.channel.telegram.TelegramUserIdFilter(allowedUsers, rateLimiter);
            filter.setDownstream(gatewayService);
            return filter;
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

    /**
     * LINE adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.line.LineAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.line", name = "enabled", havingValue = "true")
    static class LineAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.line.LineAdapter lineAdapter(JaiClawProperties properties) {
            var line = properties.channels().line();
            var config = new io.jaiclaw.channel.line.LineConfig(
                    line.channelAccessToken(),
                    line.channelSecret(),
                    line.enabled(),
                    line.allowedSenderIds());
            return new io.jaiclaw.channel.line.LineAdapter(config);
        }
    }

    /**
     * Matrix adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.matrix.MatrixAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.matrix", name = "enabled", havingValue = "true")
    static class MatrixAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.matrix.MatrixAdapter matrixAdapter(JaiClawProperties properties) {
            var matrix = properties.channels().matrix();
            var apiClient = new io.jaiclaw.channel.matrix.MatrixApiClient(
                    matrix.homeserverUrl(),
                    matrix.accessToken());
            var config = new io.jaiclaw.channel.matrix.MatrixConfig(
                    matrix.homeserverUrl(),
                    matrix.accessToken(),
                    matrix.userId(),
                    matrix.enabled(),
                    matrix.syncTimeoutMs(),
                    matrix.allowedSenderIds());
            return new io.jaiclaw.channel.matrix.MatrixAdapter(config, apiClient);
        }
    }

    /**
     * Google Chat adapter auto-configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.jaiclaw.channel.googlechat.GoogleChatAdapter")
    @ConditionalOnProperty(prefix = "jaiclaw.channels.google-chat", name = "enabled", havingValue = "true")
    static class GoogleChatAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public io.jaiclaw.channel.googlechat.GoogleChatAdapter googleChatAdapter(JaiClawProperties properties) {
            var gc = properties.channels().googleChat();
            var config = new io.jaiclaw.channel.googlechat.GoogleChatConfig(
                    gc.projectId(),
                    gc.serviceAccountKeyPath(),
                    gc.webhookPath(),
                    gc.enabled(),
                    gc.allowedSenderIds());
            return new io.jaiclaw.channel.googlechat.GoogleChatAdapter(config);
        }
    }
}

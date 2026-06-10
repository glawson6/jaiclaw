package io.jaiclaw.voicecall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.voicecall.manager.CallManager;
import io.jaiclaw.voicecall.mcp.VoiceCallMcpToolProvider;
import io.jaiclaw.voicecall.media.MediaStreamHandler;
import io.jaiclaw.voicecall.store.CallStore;
import io.jaiclaw.voicecall.store.InMemoryCallStore;
import io.jaiclaw.voicecall.telephony.TelephonyProvider;
import io.jaiclaw.voicecall.telephony.twilio.TwilioApiClient;
import io.jaiclaw.voicecall.telephony.twilio.TwilioTelephonyProvider;
import io.jaiclaw.voicecall.webhook.StaleCallReaper;
import io.jaiclaw.voicecall.webhook.WebhookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;

/**
 * Spring Boot auto-configuration for the voice-call extension.
 * Gated on {@code jaiclaw.voice-call.enabled=true}.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "jaiclaw.voice-call.enabled", havingValue = "true")
@EnableConfigurationProperties(VoiceCallProperties.class)
@EnableScheduling
@EnableWebSocket
public class VoiceCallAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TwilioApiClient twilioApiClient(VoiceCallProperties properties) {
        var twilio = properties.twilio();
        if (twilio == null || twilio.accountSid() == null || twilio.authToken() == null) {
            throw new IllegalStateException(
                    "Twilio credentials required: set jaiclaw.voice-call.twilio.account-sid and auth-token");
        }
        return new TwilioApiClient(twilio.accountSid(), twilio.authToken());
    }

    @Bean
    @ConditionalOnMissingBean
    public TelephonyProvider telephonyProvider(TwilioApiClient apiClient,
                                               VoiceCallProperties properties) {
        log.info("Registering Twilio telephony provider");
        return new TwilioTelephonyProvider(apiClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CallStore callStore(org.springframework.beans.factory.ObjectProvider<TenantGuard> tenantGuardProvider) {
        return new InMemoryCallStore(tenantGuardProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public CallManager callManager(TelephonyProvider telephonyProvider,
                                   CallStore callStore,
                                   VoiceCallProperties properties,
                                   org.springframework.beans.factory.ObjectProvider<TenantGuard> tenantGuardProvider) {
        TenantGuard guard = tenantGuardProvider.getIfAvailable();
        if (guard == null) guard = new TenantGuard(TenantProperties.DEFAULT);
        return new CallManager(telephonyProvider, callStore, properties, guard);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookController webhookController(TelephonyProvider telephonyProvider,
                                               CallManager callManager) {
        log.info("Registering voice call webhook controller");
        return new WebhookController(telephonyProvider, callManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public StaleCallReaper staleCallReaper(CallManager callManager) {
        return new StaleCallReaper(callManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public VoiceCallMcpToolProvider voiceCallMcpToolProvider(CallManager callManager,
                                                             ObjectMapper objectMapper) {
        log.info("Registering Voice Call MCP tool provider");
        return new VoiceCallMcpToolProvider(callManager, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MediaStreamHandler mediaStreamHandler(VoiceCallProperties properties,
                                                  CallManager callManager,
                                                  TelephonyProvider telephonyProvider) {
        return new MediaStreamHandler(properties, callManager, telephonyProvider);
    }

    @Bean
    public WebSocketConfigurer voiceCallWebSocketConfigurer(MediaStreamHandler handler) {
        return registry -> {
            log.info("Registering voice media stream WebSocket handler at /voice/media-stream");
            registry.addHandler(handler, "/voice/media-stream")
                    .setAllowedOrigins("*");
        };
    }
}

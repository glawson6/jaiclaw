package io.jaiclaw.messaging.config;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.gateway.GatewayService;
import io.jaiclaw.messaging.mcp.MessagingMcpToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawGatewayAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.messaging.enabled", havingValue = "true")
public class JaiClawMessagingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawMessagingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MessagingMcpProperties messagingMcpProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.messaging", MessagingMcpProperties.class)
                .orElse(new MessagingMcpProperties(true, java.util.List.of(), 50));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ChannelRegistry.class, GatewayService.class, SessionManager.class})
    public MessagingMcpToolProvider messagingMcpToolProvider(
            ChannelRegistry channelRegistry,
            GatewayService gatewayService,
            SessionManager sessionManager,
            MessagingMcpProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper;
        objectMapper;
        log.info("Registering Messaging MCP tool provider");
        return new MessagingMcpToolProvider(channelRegistry, gatewayService, sessionManager, properties, objectMapper);
    }
}

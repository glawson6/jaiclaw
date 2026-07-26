package io.jaiclaw.discord.config;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.channel.discord.DiscordConfig;
import io.jaiclaw.discord.mcp.DiscordMcpToolProvider;
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
import org.springframework.web.client.RestClient;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawChannelAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.discord.tools.enabled", havingValue = "true")
public class JaiClawDiscordToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawDiscordToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DiscordToolsProperties discordToolsProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.discord.tools", DiscordToolsProperties.class)
                .orElse(new DiscordToolsProperties(true, java.util.List.of()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.channel.discord.DiscordAdapter.class)
    public DiscordMcpToolProvider discordMcpToolProvider(
            io.jaiclaw.channel.discord.DiscordAdapter discordAdapter,
            DiscordToolsProperties properties,
            Environment environment) {
        // Read the bot token from the same config the adapter uses
        String botToken = environment.getProperty("jaiclaw.channels.discord.bot-token", "");
        ObjectMapper objectMapper = new ObjectMapper();
        log.info("Registering Discord tools MCP provider");
        return new DiscordMcpToolProvider(botToken, properties, RestClient.create(), objectMapper);
    }
}

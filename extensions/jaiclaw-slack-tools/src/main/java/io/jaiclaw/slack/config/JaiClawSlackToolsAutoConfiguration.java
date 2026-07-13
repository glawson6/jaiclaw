package io.jaiclaw.slack.config;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.slack.mcp.SlackMcpToolProvider;
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
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawChannelAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.slack.tools.enabled", havingValue = "true")
public class JaiClawSlackToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawSlackToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SlackToolsProperties slackToolsProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.slack.tools", SlackToolsProperties.class)
                .orElse(new SlackToolsProperties(true, java.util.List.of()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(io.jaiclaw.channel.slack.SlackAdapter.class)
    public SlackMcpToolProvider slackMcpToolProvider(
            io.jaiclaw.channel.slack.SlackAdapter slackAdapter,
            SlackToolsProperties properties,
            Environment environment) {
        // Read the bot token from the same config the adapter uses
        String botToken = environment.getProperty("jaiclaw.channels.slack.bot-token", "");
        ObjectMapper objectMapper = new ObjectMapper();
        log.info("Registering Slack tools MCP provider");
        return new SlackMcpToolProvider(botToken, properties, new RestTemplate(), objectMapper);
    }
}

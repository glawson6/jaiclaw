package io.jaiclaw.messaging;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.gateway.mcp.transport.server.McpStdioBridge;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * Standalone entry point for the messaging MCP server.
 *
 * <ul>
 *   <li>{@code --stdio} — runs as a stdio MCP server (no web server)</li>
 *   <li>No flag — runs as a normal Spring Boot web app (HTTP + SSE transport)</li>
 * </ul>
 */
@SpringBootApplication
public class MessagingServerApplication {

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--stdio")) {
            runStdio(args);
        } else {
            SpringApplication.run(MessagingServerApplication.class, args);
        }
    }

    private static void runStdio(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(MessagingServerApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);

        try (ConfigurableApplicationContext ctx = app.run(args)) {
            McpToolProvider provider = ctx.getBean("messagingMcpToolProvider", McpToolProvider.class);
            ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();

            McpStdioBridge bridge = new McpStdioBridge(provider, objectMapper);
            bridge.run();
        }
    }
}

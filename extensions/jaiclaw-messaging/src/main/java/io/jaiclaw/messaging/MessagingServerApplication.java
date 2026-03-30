package io.jaiclaw.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            McpStdioBridge bridge = new McpStdioBridge(provider, objectMapper);
            bridge.run();
        }
    }
}

package io.jaiclaw.examples.securityhandshake;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.security.CryptoService;
import io.jaiclaw.tools.security.HandshakeSessionStore;
import io.jaiclaw.tools.security.SecurityHandshakeProperties;
import io.jaiclaw.tools.security.SecurityTools;
import io.jaiclaw.tools.bridge.SpringAiToolBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the security handshake demo on startup.
 *
 * <p>The LLM sees two tools:
 * <ol>
 *   <li>{@code security_handshake} — performs the complete handshake, returns a session token</li>
 *   <li>{@code protected_get_secret_data} — requires the session token</li>
 * </ol>
 *
 * <p>The LLM calls {@code security_handshake} once to get a token, then uses it
 * to call the protected tool. Two tool calls total.
 */
@Configuration
public class HandshakeDemoRunner {

    private static final Logger log = LoggerFactory.getLogger(HandshakeDemoRunner.class);

    @Bean
    ApplicationRunner runHandshakeDemo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  Security Handshake Demo — Single Tool Flow");
            log.info("  Trust Model: API_KEY bootstrap + ECDH forward secrecy");
            log.info("═══════════════════════════════════════════════════════════");

            // ── Shared services ──
            CryptoService cryptoService = new CryptoService();
            HandshakeSessionStore sessionStore = new HandshakeSessionStore();

            // ── Single security handshake tool (LOCAL mode) ──
            SecurityHandshakeProperties localProps = SecurityHandshakeProperties.DEFAULT;
            List<ToolCallback> securityTools = SecurityTools.all(cryptoService, sessionStore, localProps);

            // ── Protected MCP tool that validates session tokens ──
            ProtectedMcpTool protectedTool = new ProtectedMcpTool(sessionStore);

            // ── Bridge all tools to Spring AI ──
            List<org.springframework.ai.tool.ToolCallback> springTools = new ArrayList<>();
            for (ToolCallback tool : securityTools) {
                springTools.add(new SpringAiToolBridge(tool));
            }
            springTools.add(new SpringAiToolBridge(protectedTool));

            log.info("Available tools ({}):", springTools.size());
            for (var t : springTools) {
                log.info("  - {}", t.getToolDefinition().name());
            }

            // ── Prompt: simple — the LLM just needs to call two tools ──
            String systemPrompt = """
                    You are a security agent. You have access to a security handshake tool
                    and a protected MCP server tool.

                    To access the protected data:
                    1. Call security_handshake to perform the handshake and get a sessionToken
                    2. Call protected_get_secret_data with the sessionToken

                    That's it — the handshake tool handles all the crypto internally.""";

            String userPrompt = "Access the protected MCP server's secret project data.";

            log.info("═══════════════════════════════════════════════════════════");
            log.info("  Sending to LLM: \"{}\"", userPrompt);
            log.info("═══════════════════════════════════════════════════════════");

            // ── Call the LLM ──
            ChatClient chatClient = chatClientBuilder.build();
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .toolCallbacks(springTools.toArray(new org.springframework.ai.tool.ToolCallback[0]))
                    .call()
                    .content();

            // ── Print results ──
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  LLM Response:");
            log.info("═══════════════════════════════════════════════════════════");
            log.info("\n{}", response);
            log.info("═══════════════════════════════════════════════════════════");
            log.info("  Handshake sessions in store: {}", sessionStore.size());
            log.info("═══════════════════════════════════════════════════════════");
        };
    }
}

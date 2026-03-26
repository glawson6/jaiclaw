package io.jaiclaw.tools.security;

import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration that registers the security handshake tool into the JaiClaw ToolRegistry.
 * Runs after {@code JaiClawAutoConfiguration} so that {@link ToolRegistry} is available.
 *
 * <p>Registers a single {@link SecurityHandshakeTool} — the LLM calls one tool and gets
 * back a session token. The individual handshake tools are internal implementation details.
 *
 * <p>Configuration properties (prefix: {@code jaiclaw.security.handshake}):
 * <ul>
 *   <li>{@code mode} — LOCAL (default), HTTP_CLIENT, or ORCHESTRATED</li>
 *   <li>{@code mcp-server-url} — MCP server URL for HTTP_CLIENT mode</li>
 *   <li>{@code mcp-server-name} — MCP server name for ORCHESTRATED mode</li>
 *   <li>{@code bootstrap} — bootstrap trust level: API_KEY, CLIENT_CERT, or MUTUAL</li>
 *   <li>{@code api-key} — pre-shared API key for API_KEY/MUTUAL bootstrap</li>
 *   <li>{@code allowed-client-keys} — allowed public keys for CLIENT_CERT/MUTUAL bootstrap</li>
 *   <li>{@code server.enabled} — enable server-side MCP provider and token filter</li>
 *   <li>{@code server.mcp-server-name} — MCP server name (default: "security")</li>
 *   <li>{@code server.token-ttl-seconds} — session token TTL (default: 3600)</li>
 * </ul>
 *
 * @see HandshakeSecurityAutoConfiguration for server-side auto-configuration
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class SecurityToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecurityHandshakeProperties securityHandshakeProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.security.handshake", SecurityHandshakeProperties.class)
                .orElse(SecurityHandshakeProperties.DEFAULT);
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoService cryptoService() {
        return new CryptoService();
    }

    @Bean
    @ConditionalOnMissingBean
    public HandshakeSessionStore handshakeSessionStore() {
        return new HandshakeSessionStore();
    }

    @Bean
    public SecurityToolsRegistrar securityToolsRegistrar(
            ToolRegistry toolRegistry,
            CryptoService cryptoService,
            HandshakeSessionStore sessionStore,
            SecurityHandshakeProperties properties) {
        log.info("Registering security_handshake tool into ToolRegistry (mode={}, bootstrap={})",
                properties.mode(), properties.bootstrap());
        SecurityTools.registerAll(toolRegistry, cryptoService, sessionStore, properties);
        return new SecurityToolsRegistrar();
    }

    /**
     * Embabel agent — only active when Embabel is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = "com.embabel.agent.api.annotation.Agent")
    static class EmbabelAgentConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SecurityHandshakeAgent securityHandshakeAgent(
                CryptoService cryptoService,
                HandshakeSessionStore sessionStore) {
            return new SecurityHandshakeAgent(cryptoService, sessionStore);
        }
    }

    /**
     * Marker bean to indicate that security tools have been registered.
     */
    public static class SecurityToolsRegistrar {}
}

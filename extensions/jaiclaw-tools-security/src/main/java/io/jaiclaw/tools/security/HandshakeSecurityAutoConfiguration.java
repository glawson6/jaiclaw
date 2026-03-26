package io.jaiclaw.tools.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the server-side security handshake components:
 * the {@link SecurityHandshakeMcpProvider} and the {@link HandshakeTokenFilter}.
 *
 * <p>Activated when:
 * <ul>
 *   <li>{@code jaiclaw.security.handshake.server.enabled=true}</li>
 *   <li>Spring Security's {@code OncePerRequestFilter} is on the classpath (for the filter)</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(SecurityToolsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jaiclaw.security.handshake.server", name = "enabled", havingValue = "true")
@ConditionalOnBean(SecurityToolsAutoConfiguration.SecurityToolsRegistrar.class)
public class HandshakeSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HandshakeSecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "serverHandshakeSessionStore")
    public HandshakeSessionStore serverHandshakeSessionStore() {
        return new HandshakeSessionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityHandshakeMcpProvider securityHandshakeMcpProvider(
            CryptoService cryptoService,
            HandshakeSessionStore serverHandshakeSessionStore,
            SecurityHandshakeProperties properties) {
        log.info("Registering server-side SecurityHandshakeMcpProvider (server={})",
                properties.server().mcpServerName());
        return new SecurityHandshakeMcpProvider(cryptoService, serverHandshakeSessionStore, properties);
    }

    /**
     * Token filter — only when Spring Security is on the classpath.
     */
    @ConditionalOnClass(name = "org.springframework.security.web.context.SecurityContextHolderFilter")
    static class TokenFilterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public HandshakeTokenFilter handshakeTokenFilter(
                HandshakeSessionStore serverHandshakeSessionStore,
                SecurityHandshakeProperties properties) {
            log.info("Registering HandshakeTokenFilter (skip path: /mcp/{}/**)",
                    properties.server().mcpServerName());
            return new HandshakeTokenFilter(serverHandshakeSessionStore, properties.server().mcpServerName());
        }
    }
}

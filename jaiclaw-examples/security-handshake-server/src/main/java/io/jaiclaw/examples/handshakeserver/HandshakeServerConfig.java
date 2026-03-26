package io.jaiclaw.examples.handshakeserver;

import io.jaiclaw.tools.security.CryptoService;
import io.jaiclaw.tools.security.HandshakeServerEndpoint;
import io.jaiclaw.tools.security.HandshakeSessionStore;
import io.jaiclaw.tools.security.SecurityHandshakeMcpProvider;
import io.jaiclaw.tools.security.SecurityHandshakeProperties;
import io.jaiclaw.tools.security.SecurityHandshakeProperties.ServerProperties;
import io.jaiclaw.tools.security.BootstrapTrust;
import io.jaiclaw.tools.security.HandshakeMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandshakeServerConfig {

    @Bean
    CryptoService cryptoService() {
        return new CryptoService();
    }

    @Bean
    HandshakeSessionStore handshakeSessionStore() {
        return new HandshakeSessionStore();
    }

    @Bean
    SecurityHandshakeProperties securityHandshakeProperties() {
        return new SecurityHandshakeProperties(
                HandshakeMode.LOCAL,
                null,
                null,
                BootstrapTrust.API_KEY,
                "demo-api-key-12345",
                null,
                new ServerProperties(true, "security", 3600)
        );
    }

    @Bean
    HandshakeServerEndpoint handshakeServerEndpoint(CryptoService cryptoService,
                                                     HandshakeSessionStore sessionStore,
                                                     SecurityHandshakeProperties properties) {
        return new SecurityHandshakeMcpProvider(cryptoService, sessionStore, properties);
    }

    @Bean
    ProtectedDataMcpProvider protectedDataMcpProvider() {
        return new ProtectedDataMcpProvider();
    }
}

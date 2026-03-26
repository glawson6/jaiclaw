package io.jaiclaw.examples.handshakeserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP server demonstrating the security handshake protocol.
 *
 * <p>Starts a web server with handshake endpoints at {@code /mcp/security/*}
 * and a protected data endpoint at {@code /mcp/data/tools/*}.
 * An embedded client runner automatically performs the full handshake flow
 * on startup and calls the protected tool with the resulting Bearer token.
 */
@SpringBootApplication
public class HandshakeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandshakeServerApplication.class, args);
    }
}

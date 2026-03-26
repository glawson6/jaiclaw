package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering the security handshake tool.
 *
 * <p>Exposes a single {@link SecurityHandshakeTool} to the LLM. The 6 individual
 * tools ({@code GenerateKeyPairTool}, {@code AdvertiseCapabilitiesTool}, etc.) are
 * internal implementation details — not registered as LLM-facing tools.
 */
public final class SecurityTools {

    private SecurityTools() {}

    /**
     * Create the single security handshake tool.
     */
    public static SecurityHandshakeTool handshakeTool(CryptoService cryptoService,
                                                      HandshakeSessionStore sessionStore,
                                                      SecurityHandshakeProperties properties) {
        HandshakeHttpClient httpClient = properties.mcpServerUrl() != null
                ? new HandshakeHttpClient(properties.mcpServerUrl())
                : null;
        return handshakeTool(cryptoService, sessionStore, properties, httpClient);
    }

    /**
     * Create the single security handshake tool with an explicit HTTP client.
     */
    public static SecurityHandshakeTool handshakeTool(CryptoService cryptoService,
                                                      HandshakeSessionStore sessionStore,
                                                      SecurityHandshakeProperties properties,
                                                      HandshakeHttpClient httpClient) {
        return new SecurityHandshakeTool(cryptoService, sessionStore, properties, httpClient);
    }

    /**
     * Returns all LLM-facing security tools (currently just the single handshake tool).
     */
    public static List<ToolCallback> all(CryptoService cryptoService,
                                         HandshakeSessionStore sessionStore,
                                         SecurityHandshakeProperties properties) {
        return List.of(handshakeTool(cryptoService, sessionStore, properties));
    }

    public static List<ToolCallback> all(CryptoService cryptoService,
                                         HandshakeSessionStore sessionStore,
                                         SecurityHandshakeProperties properties,
                                         HandshakeHttpClient httpClient) {
        return List.of(handshakeTool(cryptoService, sessionStore, properties, httpClient));
    }

    /**
     * Convenience overload for LOCAL mode (no MCP server URL needed).
     */
    public static List<ToolCallback> all(CryptoService cryptoService,
                                         HandshakeSessionStore sessionStore) {
        return all(cryptoService, sessionStore, SecurityHandshakeProperties.DEFAULT);
    }

    public static void registerAll(ToolRegistry registry,
                                   CryptoService cryptoService,
                                   HandshakeSessionStore sessionStore,
                                   SecurityHandshakeProperties properties) {
        registry.registerAll(all(cryptoService, sessionStore, properties));
    }

    public static void registerAll(ToolRegistry registry,
                                   CryptoService cryptoService,
                                   HandshakeSessionStore sessionStore) {
        registerAll(registry, cryptoService, sessionStore, SecurityHandshakeProperties.DEFAULT);
    }
}

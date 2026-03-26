package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;

/**
 * Advertises supported cipher suites, auth methods, and protocol version.
 * This is the discovery step — no session is created yet.
 *
 * <ul>
 *   <li><b>LOCAL</b> — returns hardcoded capabilities</li>
 *   <li><b>HTTP_CLIENT</b> — queries the remote MCP server for its capabilities</li>
 *   <li><b>ORCHESTRATED</b> — returns client-side capabilities for the LLM to
 *       compare with the MCP server's capabilities</li>
 * </ul>
 */
public class AdvertiseCapabilitiesTool extends AbstractSecurityTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {},
              "required": []
            }""";

    private final HandshakeHttpClient httpClient;

    public AdvertiseCapabilitiesTool(CryptoService cryptoService, HandshakeSessionStore sessionStore,
                                    SecurityHandshakeProperties properties, HandshakeHttpClient httpClient) {
        super(new ToolDefinition(
                "security_advertise_capabilities",
                "Advertise supported cipher suites, authentication methods, and protocol version for the security handshake.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), cryptoService, sessionStore, properties);
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
        return switch (mode()) {
            case HTTP_CLIENT -> {
                String response = httpClient.post("/capabilities", Map.of());
                yield new ToolResult.Success(response);
            }
            case ORCHESTRATED -> new ToolResult.Success(clientCapabilities());
            case LOCAL -> new ToolResult.Success(localCapabilities());
        };
    }

    private static String localCapabilities() {
        return """
                {
                  "protocolVersion": "1.0",
                  "cipherSuites": [
                    "ECDH-X25519-AES256-GCM-SHA384",
                    "ECDH-P256-AES128-GCM-SHA256",
                    "DH-AES256-CBC-SHA256"
                  ],
                  "authMethods": [
                    "HMAC-SHA256",
                    "JWT"
                  ],
                  "keyExchangeAlgorithms": [
                    "X25519",
                    "P-256"
                  ]
                }""";
    }

    private static String clientCapabilities() {
        return """
                {
                  "side": "client",
                  "protocolVersion": "1.0",
                  "supportedCipherSuites": [
                    "ECDH-X25519-AES256-GCM-SHA384",
                    "ECDH-P256-AES128-GCM-SHA256",
                    "DH-AES256-CBC-SHA256"
                  ],
                  "supportedAuthMethods": [
                    "HMAC-SHA256",
                    "JWT"
                  ],
                  "instructions": "Query the MCP server's capabilities and select the best matching cipher suite and auth method."
                }""";
    }
}

package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;

/**
 * Final handshake step — derives a session token and marks the handshake complete.
 * The session token can be used as a Bearer token for subsequent MCP tool calls.
 *
 * <ul>
 *   <li><b>LOCAL</b> — creates JWT session token from shared secret</li>
 *   <li><b>HTTP_CLIENT</b> — requests session token from MCP server</li>
 *   <li><b>ORCHESTRATED</b> — receives token from MCP server via LLM, or creates locally</li>
 * </ul>
 */
public class EstablishContextTool extends AbstractSecurityTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "handshakeId": {
                  "type": "string",
                  "description": "The handshake session ID"
                },
                "ttlSeconds": {
                  "type": "integer",
                  "description": "Session token TTL in seconds (default: 3600)"
                },
                "serverSessionToken": {
                  "type": "string",
                  "description": "Session token from MCP server (ORCHESTRATED mode only)"
                }
              },
              "required": ["handshakeId"]
            }""";

    private final HandshakeHttpClient httpClient;

    public EstablishContextTool(CryptoService cryptoService, HandshakeSessionStore sessionStore,
                                SecurityHandshakeProperties properties, HandshakeHttpClient httpClient) {
        super(new ToolDefinition(
                "security_establish_context",
                "Finalize the handshake — derive a session token for authenticating subsequent MCP tool calls.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), cryptoService, sessionStore, properties);
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String handshakeId = requireParam(parameters, "handshakeId");
        int ttlSeconds = optionalIntParam(parameters, "ttlSeconds", 3600);

        return switch (mode()) {
            case LOCAL -> executeLocal(handshakeId, ttlSeconds);
            case HTTP_CLIENT -> executeHttpClient(handshakeId, ttlSeconds);
            case ORCHESTRATED -> executeOrchestrated(handshakeId, ttlSeconds, parameters);
        };
    }

    private ToolResult executeLocal(String handshakeId, int ttlSeconds) {
        HandshakeSession session = sessionStore.require(handshakeId);

        if (!session.isIdentityVerified()) {
            return new ToolResult.Error("Identity has not been verified for this session");
        }
        if (session.getSharedSecret() == null) {
            return new ToolResult.Error("Key exchange has not been completed for this session");
        }

        String sessionToken = cryptoService.createSessionToken(
                session.getSharedSecret(),
                session.getVerifiedSubject(),
                Map.of(
                        "handshakeId", handshakeId,
                        "cipherSuite", session.getSelectedCipherSuite(),
                        "keyFingerprint", session.getKeyFingerprint()
                ),
                ttlSeconds);

        session.setSessionToken(sessionToken);
        session.setCompleted(true);

        return new ToolResult.Success(formatResult(handshakeId, sessionToken,
                session.getSelectedCipherSuite(), ttlSeconds,
                session.getVerifiedSubject(), session.getAlgorithm()));
    }

    private ToolResult executeHttpClient(String handshakeId, int ttlSeconds) {
        HandshakeSession session = sessionStore.require(handshakeId);

        // Request session token from MCP server
        String response = httpClient.post("/establish", Map.of(
                "handshakeId", handshakeId,
                "ttlSeconds", ttlSeconds
        ));

        // Parse and store token
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(response);
            String sessionToken = node.get("sessionToken").asText();
            session.setSessionToken(sessionToken);
            session.setCompleted(true);

            return new ToolResult.Success(response);
        } catch (Exception e) {
            return new ToolResult.Error("Failed to parse establish response: " + e.getMessage());
        }
    }

    private ToolResult executeOrchestrated(String handshakeId, int ttlSeconds,
                                            Map<String, Object> parameters) {
        HandshakeSession session = sessionStore.require(handshakeId);
        String serverSessionToken = optionalParam(parameters, "serverSessionToken", null);

        if (serverSessionToken != null) {
            // LLM has provided the token from the MCP server
            session.setSessionToken(serverSessionToken);
            session.setCompleted(true);

            return new ToolResult.Success(String.format("""
                    {
                      "handshakeId": "%s",
                      "sessionToken": "%s",
                      "cipherSuite": "%s",
                      "expiresInSeconds": %d,
                      "mode": "ORCHESTRATED",
                      "summary": "Security handshake complete. Use this sessionToken as a Bearer token for subsequent MCP tool calls."
                    }""",
                    handshakeId, serverSessionToken,
                    session.getSelectedCipherSuite(), ttlSeconds));
        }

        // Instruct LLM to get token from MCP server
        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "mode": "ORCHESTRATED",
                  "instructions": "Call the MCP server's establish endpoint with handshakeId '%s' and ttlSeconds %d. Then call this tool again with the serverSessionToken."
                }""",
                handshakeId, handshakeId, ttlSeconds));
    }

    private String formatResult(String handshakeId, String sessionToken, String cipherSuite,
                                int ttlSeconds, String subject, String algorithm) {
        return String.format("""
                {
                  "handshakeId": "%s",
                  "sessionToken": "%s",
                  "cipherSuite": "%s",
                  "expiresInSeconds": %d,
                  "summary": "Security handshake complete. Session established for '%s' using %s with %s key exchange. Use this sessionToken as a Bearer token for subsequent MCP tool calls."
                }""",
                handshakeId, sessionToken, cipherSuite, ttlSeconds, subject, cipherSuite, algorithm);
    }
}

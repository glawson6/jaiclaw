package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.util.Map;
import java.util.Set;

/**
 * Issues a nonce challenge for identity verification.
 *
 * <ul>
 *   <li><b>LOCAL</b> — generates challenge nonce, stores in session</li>
 *   <li><b>HTTP_CLIENT</b> — requests challenge from MCP server, stores nonce locally</li>
 *   <li><b>ORCHESTRATED</b> — returns instructions for LLM to request challenge from MCP server</li>
 * </ul>
 */
public class ChallengeResponseTool extends AbstractSecurityTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "handshakeId": {
                  "type": "string",
                  "description": "The handshake session ID from the negotiate step"
                },
                "challenge": {
                  "type": "string",
                  "description": "Challenge nonce from MCP server (ORCHESTRATED mode only)"
                }
              },
              "required": ["handshakeId"]
            }""";

    private final HandshakeHttpClient httpClient;

    public ChallengeResponseTool(CryptoService cryptoService, HandshakeSessionStore sessionStore,
                                 SecurityHandshakeProperties properties, HandshakeHttpClient httpClient) {
        super(new ToolDefinition(
                "security_challenge_response",
                "Issue or receive a nonce challenge for identity verification.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), cryptoService, sessionStore, properties);
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String handshakeId = requireParam(parameters, "handshakeId");

        return switch (mode()) {
            case LOCAL -> executeLocal(handshakeId);
            case HTTP_CLIENT -> executeHttpClient(handshakeId);
            case ORCHESTRATED -> executeOrchestrated(handshakeId, parameters);
        };
    }

    private ToolResult executeLocal(String handshakeId) {
        HandshakeSession session = sessionStore.require(handshakeId);
        if (session.getSharedSecret() == null) {
            return new ToolResult.Error("Key exchange has not been completed for this session");
        }

        String challengeNonce = cryptoService.generateNonce();
        session.setChallengeNonce(challengeNonce);

        // LOCAL mode: compute the HMAC internally and store a verification token.
        // The credential returned is a reference token — not the raw HMAC — so the
        // shared secret never leaks to the LLM. VerifyIdentityTool auto-verifies
        // in LOCAL mode when it sees a matching reference token.
        String hmac = cryptoService.hmacSign(session.getSharedSecret(), challengeNonce);
        String referenceToken = "local-ref:" + cryptoService.sha256Fingerprint(
                hmac.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "challenge": "%s",
                  "credential": "%s",
                  "authMethod": "%s",
                  "mode": "LOCAL",
                  "note": "LOCAL mode auto-verifies — use HTTP_CLIENT or ORCHESTRATED for real authentication",
                  "instructions": "Pass the credential value to security_verify_identity as the credential parameter."
                }""",
                handshakeId, challengeNonce, referenceToken, session.getSelectedAuthMethod()));
    }

    private ToolResult executeHttpClient(String handshakeId) {
        HandshakeSession session = sessionStore.require(handshakeId);

        // Request challenge from MCP server
        String response = httpClient.post("/challenge", Map.of("handshakeId", handshakeId));

        // Parse and store the challenge nonce locally
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(response);
            String challengeNonce = node.get("challenge").asText();
            session.setChallengeNonce(challengeNonce);

            // Compute the HMAC signature locally using our shared secret
            String signature = cryptoService.hmacSign(session.getSharedSecret(), challengeNonce);

            return new ToolResult.Success(String.format("""
                    {
                      "handshakeId": "%s",
                      "challenge": "%s",
                      "signature": "%s",
                      "authMethod": "%s",
                      "mode": "HTTP_CLIENT",
                      "instructions": "The signature has been computed. Proceed to verify identity."
                    }""",
                    handshakeId, challengeNonce, signature, session.getSelectedAuthMethod()));
        } catch (Exception e) {
            return new ToolResult.Error("Failed to parse challenge response: " + e.getMessage());
        }
    }

    private ToolResult executeOrchestrated(String handshakeId, Map<String, Object> parameters) {
        HandshakeSession session = sessionStore.require(handshakeId);
        String challenge = optionalParam(parameters, "challenge", null);

        if (challenge != null) {
            // LLM has provided the challenge from the MCP server — sign it
            if (session.getSharedSecret() == null) {
                return new ToolResult.Error("Key exchange has not been completed for this session");
            }

            session.setChallengeNonce(challenge);
            String signature = cryptoService.hmacSign(session.getSharedSecret(), challenge);

            return new ToolResult.Success(String.format("""
                    {
                      "handshakeId": "%s",
                      "challenge": "%s",
                      "signature": "%s",
                      "mode": "ORCHESTRATED",
                      "instructions": "Send this signature to the MCP server's verify endpoint."
                    }""",
                    handshakeId, challenge, signature));
        }

        // No challenge yet — instruct LLM to get one from MCP server
        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "mode": "ORCHESTRATED",
                  "instructions": "Request a challenge from the MCP server by calling its challenge endpoint with handshakeId '%s'. Then call this tool again with the challenge nonce."
                }""",
                handshakeId, handshakeId));
    }
}

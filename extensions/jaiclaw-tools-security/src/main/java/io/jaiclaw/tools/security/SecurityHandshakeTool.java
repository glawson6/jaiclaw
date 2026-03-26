package io.jaiclaw.tools.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;

/**
 * Single LLM-facing tool that performs the complete security handshake.
 *
 * <p>The calling LLM sees one tool: {@code security_handshake}. Internally,
 * this tool deterministically executes the full handshake flow:
 * <ol>
 *   <li>Discover server capabilities (cipher suites, auth methods)</li>
 *   <li>Generate client key pair</li>
 *   <li>Negotiate session (ECDH key exchange)</li>
 *   <li>Challenge-response (HMAC-SHA256)</li>
 *   <li>Verify identity</li>
 *   <li>Establish session — get JWT token</li>
 * </ol>
 *
 * <p>Supports all three {@link HandshakeMode}s:
 * <ul>
 *   <li><b>LOCAL</b> — both client and server in-process</li>
 *   <li><b>HTTP_CLIENT</b> — calls remote MCP server endpoints</li>
 *   <li><b>ORCHESTRATED</b> — same as HTTP_CLIENT (no LLM in the loop)</li>
 * </ul>
 *
 * <p>Returns: {@code { sessionToken, handshakeId, cipherSuite, expiresInSeconds }}
 */
public class SecurityHandshakeTool extends AbstractBuiltinTool {

    private static final Logger log = LoggerFactory.getLogger(SecurityHandshakeTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "mcpServerUrl": {
                  "type": "string",
                  "description": "MCP server URL to handshake with (optional — defaults from config)"
                },
                "clientId": {
                  "type": "string",
                  "description": "Client identifier (optional — defaults to agent ID from context)"
                }
              },
              "required": []
            }""";

    private final CryptoService cryptoService;
    private final HandshakeSessionStore sessionStore;
    private final SecurityHandshakeProperties properties;
    private final HandshakeHttpClient httpClient;

    public SecurityHandshakeTool(CryptoService cryptoService,
                                 HandshakeSessionStore sessionStore,
                                 SecurityHandshakeProperties properties,
                                 HandshakeHttpClient httpClient) {
        super(new ToolDefinition(
                "security_handshake",
                "Perform a complete security handshake with an MCP server — ECDH key exchange, "
                        + "challenge-response authentication, and JWT session establishment. "
                        + "Returns a session token for authenticating subsequent MCP tool calls.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
        this.cryptoService = cryptoService;
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String clientId = optionalParam(parameters, "clientId", context.agentId());

        return switch (properties.mode()) {
            case LOCAL -> executeLocal(clientId);
            case HTTP_CLIENT, ORCHESTRATED -> executeHttpClient(clientId, parameters);
        };
    }

    /**
     * LOCAL mode: both client and server sides run in-process.
     */
    private ToolResult executeLocal(String clientId) {
        log.debug("Starting LOCAL handshake for clientId={}", clientId);

        // Step 1: select cipher suite (hardcoded — LOCAL mode uses P-256)
        String cipherSuite = "ECDH-P256-AES128-GCM-SHA256";
        String authMethod = "HMAC-SHA256";

        // Step 2: generate client key pair
        KeyPair clientKeyPair = cryptoService.generateP256KeyPair();
        String clientPublicKeyEncoded = cryptoService.encodePublicKey(clientKeyPair.getPublic());

        // Step 3: negotiate — server side generates key pair and performs key agreement
        KeyPair serverKeyPair = cryptoService.generateP256KeyPair();
        byte[] sharedSecret = cryptoService.keyAgreement(
                serverKeyPair.getPrivate(), clientKeyPair.getPublic(), "ECDH");
        String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);

        HandshakeSession session = sessionStore.create();
        session.setSelectedCipherSuite(cipherSuite);
        session.setSelectedAuthMethod(authMethod);
        session.setClientId(clientId);
        session.setClientNonce(cryptoService.generateNonce());
        session.setServerNonce(cryptoService.generateNonce());
        session.setServerKeyPair(serverKeyPair);
        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(fingerprint);
        session.setAlgorithm("ECDH");

        // Step 4: challenge-response — generate nonce, compute HMAC
        String challengeNonce = cryptoService.generateNonce();
        session.setChallengeNonce(challengeNonce);
        String hmac = cryptoService.hmacSign(sharedSecret, challengeNonce);

        // Step 5: verify — auto-verify in LOCAL mode
        boolean verified = cryptoService.hmacVerify(sharedSecret, challengeNonce, hmac);
        session.setIdentityVerified(verified);
        session.setVerifiedSubject(clientId);

        if (!verified) {
            return new ToolResult.Error("LOCAL handshake failed: HMAC verification failed");
        }

        // Step 6: establish session — create JWT
        int ttlSeconds = properties.server().tokenTtlSeconds();
        String sessionToken = cryptoService.createSessionToken(
                sharedSecret, clientId,
                Map.of(
                        "handshakeId", session.getHandshakeId(),
                        "cipherSuite", cipherSuite,
                        "keyFingerprint", fingerprint
                ),
                ttlSeconds);

        session.setSessionToken(sessionToken);
        session.setCompleted(true);

        log.info("LOCAL handshake complete: handshakeId={}, clientId={}", session.getHandshakeId(), clientId);

        return new ToolResult.Success(formatResult(
                session.getHandshakeId(), sessionToken, cipherSuite, ttlSeconds));
    }

    /**
     * HTTP_CLIENT mode: calls remote MCP server endpoints.
     */
    private ToolResult executeHttpClient(String clientId, Map<String, Object> parameters) throws Exception {
        String mcpServerUrl = optionalParam(parameters, "mcpServerUrl", properties.mcpServerUrl());
        HandshakeHttpClient client = this.httpClient;
        if (mcpServerUrl != null && (client == null || !mcpServerUrl.equals(properties.mcpServerUrl()))) {
            client = new HandshakeHttpClient(mcpServerUrl);
        }
        if (client == null) {
            return new ToolResult.Error("No MCP server URL configured. "
                    + "Provide mcpServerUrl parameter or set jaiclaw.security.handshake.mcp-server-url");
        }

        log.debug("Starting HTTP_CLIENT handshake for clientId={}", clientId);

        // Step 1: discover capabilities
        String capabilitiesResponse = client.post("/capabilities", Map.of());
        var capNode = MAPPER.readTree(capabilitiesResponse);
        String cipherSuite = capNode.get("cipherSuites").get(0).asText();
        String authMethod = capNode.get("authMethods").get(0).asText();

        // Step 2: generate client key pair
        boolean useX25519 = cipherSuite.contains("X25519");
        String keyAlgorithm = useX25519 ? "X25519" : "EC";
        String agreementAlgorithm = useX25519 ? "XDH" : "ECDH";

        KeyPair clientKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();
        String clientPublicKeyEncoded = cryptoService.encodePublicKey(clientKeyPair.getPublic());

        // Step 3: negotiate session
        var negotiatePayload = new java.util.HashMap<String, Object>();
        negotiatePayload.put("clientPublicKey", clientPublicKeyEncoded);
        negotiatePayload.put("cipherSuite", cipherSuite);
        negotiatePayload.put("authMethod", authMethod);
        negotiatePayload.put("clientId", clientId);
        negotiatePayload.put("clientNonce", cryptoService.generateNonce());

        String apiKey = properties.apiKey();
        if (apiKey != null) {
            negotiatePayload.put("apiKey", apiKey);
        }

        String negotiateResponse = client.post("/negotiate", negotiatePayload);
        var negNode = MAPPER.readTree(negotiateResponse);
        String serverPublicKeyEncoded = negNode.get("serverPublicKey").asText();
        String handshakeId = negNode.get("handshakeId").asText();

        // Perform key agreement locally
        PublicKey serverPublicKey = cryptoService.decodePublicKey(serverPublicKeyEncoded, keyAlgorithm);
        byte[] sharedSecret = cryptoService.keyAgreement(
                clientKeyPair.getPrivate(), serverPublicKey, agreementAlgorithm);

        // Store session locally
        HandshakeSession session = new HandshakeSession(handshakeId);
        session.setSelectedCipherSuite(cipherSuite);
        session.setSelectedAuthMethod(authMethod);
        session.setClientId(clientId);
        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(cryptoService.sha256Fingerprint(sharedSecret));
        session.setAlgorithm(agreementAlgorithm);
        sessionStore.put(handshakeId, session);

        // Step 4: request challenge
        String challengeResponse = client.post("/challenge", Map.of("handshakeId", handshakeId));
        var chalNode = MAPPER.readTree(challengeResponse);
        String challengeNonce = chalNode.get("challenge").asText();
        session.setChallengeNonce(challengeNonce);

        // Sign the challenge with our shared secret
        String signature = cryptoService.hmacSign(sharedSecret, challengeNonce);

        // Step 5: verify identity
        String verifyResponse = client.post("/verify", Map.of(
                "handshakeId", handshakeId,
                "method", authMethod,
                "credential", signature
        ));
        var verifyNode = MAPPER.readTree(verifyResponse);
        boolean verified = verifyNode.get("verified").asBoolean();

        if (!verified) {
            return new ToolResult.Error("Handshake failed: server rejected identity verification");
        }
        session.setIdentityVerified(true);
        session.setVerifiedSubject(clientId);

        // Step 6: establish session
        int ttlSeconds = properties.server().tokenTtlSeconds();
        String establishResponse = client.post("/establish", Map.of(
                "handshakeId", handshakeId,
                "ttlSeconds", ttlSeconds
        ));
        var estNode = MAPPER.readTree(establishResponse);
        String sessionToken = estNode.get("sessionToken").asText();

        session.setSessionToken(sessionToken);
        session.setCompleted(true);

        log.info("HTTP_CLIENT handshake complete: handshakeId={}, clientId={}", handshakeId, clientId);

        return new ToolResult.Success(formatResult(handshakeId, sessionToken, cipherSuite, ttlSeconds));
    }

    private static String formatResult(String handshakeId, String sessionToken,
                                        String cipherSuite, int expiresInSeconds) {
        return String.format("""
                {
                  "handshakeId": "%s",
                  "sessionToken": "%s",
                  "cipherSuite": "%s",
                  "expiresInSeconds": %d,
                  "summary": "Security handshake complete. Use sessionToken as a Bearer token for subsequent MCP tool calls."
                }""",
                handshakeId, sessionToken, cipherSuite, expiresInSeconds);
    }
}

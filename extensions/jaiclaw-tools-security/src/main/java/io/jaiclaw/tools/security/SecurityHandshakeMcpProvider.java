package io.jaiclaw.tools.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

/**
 * Server-side MCP tool provider implementing the security handshake protocol.
 * Registered at {@code /mcp/{serverName}} (default: {@code /mcp/security}).
 *
 * <p>Implements {@link HandshakeServerEndpoint} with typed request/response records.
 * The MCP {@link #execute} method deserializes JSON args into typed requests,
 * delegates to the interface methods, and serializes responses back to JSON.
 *
 * <p>Exposes 5 MCP tools:
 * <ul>
 *   <li>{@code security_server_capabilities} — returns supported cipher suites and auth methods</li>
 *   <li>{@code security_server_negotiate} — validates bootstrap credential, performs ECDH, creates session</li>
 *   <li>{@code security_server_challenge} — issues nonce challenge</li>
 *   <li>{@code security_server_verify} — verifies client's HMAC response</li>
 *   <li>{@code security_server_establish} — creates JWT session token</li>
 * </ul>
 */
public class SecurityHandshakeMcpProvider implements McpToolProvider, HandshakeServerEndpoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityHandshakeMcpProvider.class);

    private final CryptoService cryptoService;
    private final HandshakeSessionStore sessionStore;
    private final SecurityHandshakeProperties properties;
    private final BootstrapValidator bootstrapValidator;
    private final ObjectMapper objectMapper;

    public SecurityHandshakeMcpProvider(CryptoService cryptoService,
                                         HandshakeSessionStore sessionStore,
                                         SecurityHandshakeProperties properties) {
        this.cryptoService = cryptoService;
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.bootstrapValidator = createValidator(properties);
        this.objectMapper = new ObjectMapper();
    }

    private static BootstrapValidator createValidator(SecurityHandshakeProperties properties) {
        if (properties.bootstrap() == null) {
            return null;
        }
        return switch (properties.bootstrap()) {
            case API_KEY -> new ApiKeyBootstrapValidator();
            case CLIENT_CERT -> new ClientCertBootstrapValidator(new CryptoService());
            case MUTUAL -> new MutualBootstrapValidator(new CryptoService());
        };
    }

    @Override
    public String getServerName() {
        return properties.server().mcpServerName();
    }

    @Override
    public String getServerDescription() {
        return "Security handshake server — ECDH key exchange, challenge-response authentication, and session token management";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("security_server_capabilities",
                        "Returns the server's supported cipher suites and authentication methods",
                        CAPABILITIES_SCHEMA),
                new McpToolDefinition("security_server_negotiate",
                        "Validate bootstrap credential and perform ECDH key exchange",
                        NEGOTIATE_SCHEMA),
                new McpToolDefinition("security_server_challenge",
                        "Issue a nonce challenge for identity verification",
                        CHALLENGE_SCHEMA),
                new McpToolDefinition("security_server_verify",
                        "Verify the client's HMAC signature of the challenge nonce",
                        VERIFY_SCHEMA),
                new McpToolDefinition("security_server_establish",
                        "Create a JWT session token for the authenticated client",
                        ESTABLISH_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "security_server_capabilities" -> toResult(capabilities());
                case "security_server_negotiate" -> toResult(negotiate(toNegotiateRequest(args)));
                case "security_server_challenge" -> toResult(challenge(toChallengeRequest(args)));
                case "security_server_verify" -> toResult(verify(toVerifyRequest(args)));
                case "security_server_establish" -> toResult(establish(toEstablishRequest(args)));
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (SecurityException e) {
            log.warn("Security handshake bootstrap failed: {}", e.getMessage());
            return McpToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Security handshake MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    // ── HandshakeServerEndpoint typed methods ──

    @Override
    public CapabilitiesResponse capabilities() {
        String bootstrapTrust = properties.bootstrap() != null
                ? properties.bootstrap().name() : "NONE";
        return new CapabilitiesResponse(
                List.of("ECDH-X25519-AES256-GCM-SHA384",
                        "ECDH-P256-AES128-GCM-SHA256",
                        "DH-AES256-CBC-SHA256"),
                List.of("HMAC-SHA256", "JWT"),
                List.of("ECDH", "XDH"),
                bootstrapTrust,
                true
        );
    }

    @Override
    public NegotiateResponse negotiate(NegotiateRequest request) {
        // Validate bootstrap credentials if configured
        if (bootstrapValidator != null) {
            Map<String, Object> args = toMap(request);
            if (!bootstrapValidator.validate(args, properties)) {
                throw new SecurityException("Bootstrap authentication failed: " + bootstrapValidator.failureReason());
            }
        }

        String clientPublicKeyEncoded = requireNonNull(request.clientPublicKey(), "clientPublicKey");
        String cipherSuite = requireNonNull(request.cipherSuite(), "cipherSuite");
        String authMethod = requireNonNull(request.authMethod(), "authMethod");
        String clientId = requireNonNull(request.clientId(), "clientId");
        String clientNonce = request.clientNonce();

        boolean useX25519 = cipherSuite.contains("X25519");
        String keyAlgorithm = useX25519 ? "X25519" : "EC";
        String agreementAlgorithm = useX25519 ? "XDH" : "ECDH";

        PublicKey clientPublicKey = cryptoService.decodePublicKey(clientPublicKeyEncoded, keyAlgorithm);
        KeyPair serverKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();
        byte[] sharedSecret = cryptoService.keyAgreement(
                serverKeyPair.getPrivate(), clientPublicKey, agreementAlgorithm);
        String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);

        HandshakeSession session = sessionStore.create();
        session.setSelectedCipherSuite(cipherSuite);
        session.setSelectedAuthMethod(authMethod);
        session.setClientId(clientId);
        session.setClientNonce(clientNonce);
        session.setServerNonce(cryptoService.generateNonce());
        session.setServerKeyPair(serverKeyPair);
        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(fingerprint);
        session.setAlgorithm(agreementAlgorithm);

        String serverPublicKeyEncoded = cryptoService.encodePublicKey(serverKeyPair.getPublic());

        // For MUTUAL bootstrap, server signs its nonce to prove identity
        String serverSignature = null;
        if (properties.bootstrap() == BootstrapTrust.MUTUAL) {
            serverSignature = cryptoService.hmacSign(sharedSecret,
                    session.getServerNonce() + (clientNonce != null ? clientNonce : ""));
        }

        log.info("Negotiate completed for client '{}' — handshake {}", clientId, session.getHandshakeId());
        return new NegotiateResponse(
                session.getHandshakeId(),
                serverPublicKeyEncoded,
                fingerprint,
                agreementAlgorithm,
                session.getServerNonce(),
                true,
                serverSignature
        );
    }

    @Override
    public ChallengeResponse challenge(ChallengeRequest request) {
        String handshakeId = requireNonNull(request.handshakeId(), "handshakeId");
        HandshakeSession session = sessionStore.require(handshakeId);

        if (session.getSharedSecret() == null) {
            throw new IllegalStateException("Key exchange has not been completed for this session");
        }

        String challengeNonce = cryptoService.generateNonce();
        session.setChallengeNonce(challengeNonce);

        log.debug("Challenge issued for handshake {}", handshakeId);
        return new ChallengeResponse(handshakeId, challengeNonce, session.getSelectedAuthMethod());
    }

    @Override
    public VerifyResponse verify(VerifyRequest request) {
        String handshakeId = requireNonNull(request.handshakeId(), "handshakeId");
        String method = requireNonNull(request.method(), "method");
        String credential = requireNonNull(request.credential(), "credential");

        HandshakeSession session = sessionStore.require(handshakeId);

        if (session.getChallengeNonce() == null) {
            throw new IllegalStateException("No challenge has been issued for this session");
        }
        if (session.getSharedSecret() == null) {
            throw new IllegalStateException("Key exchange has not been completed for this session");
        }

        boolean verified = false;
        if ("HMAC-SHA256".equalsIgnoreCase(method) || "JWT".equalsIgnoreCase(method)) {
            verified = cryptoService.hmacVerify(
                    session.getSharedSecret(), session.getChallengeNonce(), credential);
        }

        session.setIdentityVerified(verified);
        if (verified) {
            session.setVerifiedSubject(session.getClientId());
        }

        String details = verified
                ? method + " signature verified against challenge nonce"
                : method + " signature mismatch";

        log.info("Verify {} for handshake {} — {}", verified ? "PASSED" : "FAILED", handshakeId, details);
        return new VerifyResponse(handshakeId, method, verified,
                verified ? session.getClientId() : null, details);
    }

    @Override
    public EstablishResponse establish(EstablishRequest request) {
        String handshakeId = requireNonNull(request.handshakeId(), "handshakeId");
        int ttlSeconds = request.ttlSeconds() != null
                ? request.ttlSeconds()
                : properties.server().tokenTtlSeconds();

        HandshakeSession session = sessionStore.require(handshakeId);

        if (!session.isIdentityVerified()) {
            throw new IllegalStateException("Identity has not been verified for this session");
        }
        if (session.getSharedSecret() == null) {
            throw new IllegalStateException("Key exchange has not been completed for this session");
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

        log.info("Session established for '{}' — handshake {} (TTL: {}s)",
                session.getVerifiedSubject(), handshakeId, ttlSeconds);
        return new EstablishResponse(
                handshakeId,
                sessionToken,
                session.getSelectedCipherSuite(),
                ttlSeconds,
                session.getVerifiedSubject(),
                "Security handshake complete. Use this sessionToken as a Bearer token for subsequent MCP tool calls."
        );
    }

    // ── conversion helpers ──

    private NegotiateRequest toNegotiateRequest(Map<String, Object> args) {
        return new NegotiateRequest(
                stringOrNull(args, "clientPublicKey"),
                stringOrNull(args, "cipherSuite"),
                stringOrNull(args, "authMethod"),
                stringOrNull(args, "clientId"),
                stringOrNull(args, "clientNonce"),
                stringOrNull(args, "apiKey"),
                stringOrNull(args, "clientSignature")
        );
    }

    private ChallengeRequest toChallengeRequest(Map<String, Object> args) {
        return new ChallengeRequest(stringOrNull(args, "handshakeId"));
    }

    private VerifyRequest toVerifyRequest(Map<String, Object> args) {
        return new VerifyRequest(
                stringOrNull(args, "handshakeId"),
                stringOrNull(args, "method"),
                stringOrNull(args, "credential")
        );
    }

    private EstablishRequest toEstablishRequest(Map<String, Object> args) {
        Integer ttl = args.containsKey("ttlSeconds")
                ? ((Number) args.get("ttlSeconds")).intValue()
                : null;
        return new EstablishRequest(stringOrNull(args, "handshakeId"), ttl);
    }

    private Map<String, Object> toMap(NegotiateRequest req) {
        // Reconstruct the raw map for bootstrap validator compatibility
        var map = new java.util.HashMap<String, Object>();
        if (req.clientPublicKey() != null) map.put("clientPublicKey", req.clientPublicKey());
        if (req.cipherSuite() != null) map.put("cipherSuite", req.cipherSuite());
        if (req.authMethod() != null) map.put("authMethod", req.authMethod());
        if (req.clientId() != null) map.put("clientId", req.clientId());
        if (req.clientNonce() != null) map.put("clientNonce", req.clientNonce());
        if (req.apiKey() != null) map.put("apiKey", req.apiKey());
        if (req.clientSignature() != null) map.put("clientSignature", req.clientSignature());
        return map;
    }

    private McpToolResult toResult(Object response) {
        try {
            return McpToolResult.success(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            return McpToolResult.error("Failed to serialize response: " + e.getMessage());
        }
    }

    private static String stringOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? value.toString() : null;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        return value;
    }

    // --- JSON Schema constants ---

    private static final String CAPABILITIES_SCHEMA = """
            {"type": "object", "properties": {}}""";

    private static final String NEGOTIATE_SCHEMA = """
            {"type": "object", "properties": {\
            "clientPublicKey": {"type": "string", "description": "Base64url-encoded client public key"},\
            "cipherSuite": {"type": "string", "description": "Selected cipher suite"},\
            "authMethod": {"type": "string", "description": "Authentication method: HMAC-SHA256 or JWT"},\
            "clientId": {"type": "string", "description": "Client identifier"},\
            "clientNonce": {"type": "string", "description": "Client-generated nonce"},\
            "apiKey": {"type": "string", "description": "Pre-shared API key (for API_KEY/MUTUAL bootstrap)"},\
            "clientSignature": {"type": "string", "description": "Client nonce signature (for CLIENT_CERT/MUTUAL bootstrap)"}\
            }, "required": ["clientPublicKey", "cipherSuite", "authMethod", "clientId"]}""";

    private static final String CHALLENGE_SCHEMA = """
            {"type": "object", "properties": {\
            "handshakeId": {"type": "string", "description": "Handshake session ID"}\
            }, "required": ["handshakeId"]}""";

    private static final String VERIFY_SCHEMA = """
            {"type": "object", "properties": {\
            "handshakeId": {"type": "string", "description": "Handshake session ID"},\
            "method": {"type": "string", "description": "Verification method: HMAC-SHA256 or JWT"},\
            "credential": {"type": "string", "description": "Signed credential (HMAC signature or JWT)"}\
            }, "required": ["handshakeId", "method", "credential"]}""";

    private static final String ESTABLISH_SCHEMA = """
            {"type": "object", "properties": {\
            "handshakeId": {"type": "string", "description": "Handshake session ID"},\
            "ttlSeconds": {"type": "integer", "description": "Token TTL in seconds (default: from config)"}\
            }, "required": ["handshakeId"]}""";
}

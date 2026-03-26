package io.jaiclaw.tools.security;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;

/**
 * ECDH key exchange — creates a handshake session, generates a key pair,
 * and performs key agreement.
 *
 * <ul>
 *   <li><b>LOCAL</b> — both sides in-process: accepts client public key, generates
 *       server key pair, performs key agreement, returns server public key</li>
 *   <li><b>HTTP_CLIENT</b> — generates client key pair locally, sends client public key
 *       to the MCP server's negotiate endpoint, receives server public key, performs
 *       key agreement locally to derive shared secret</li>
 *   <li><b>ORCHESTRATED</b> — generates client key pair and returns the public key
 *       for the LLM to forward to the MCP server. The LLM then calls back with the
 *       server's public key to complete the exchange.</li>
 * </ul>
 */
public class NegotiateSessionTool extends AbstractSecurityTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "clientPublicKey": {
                  "type": "string",
                  "description": "Base64url-encoded client public key (LOCAL mode only, ignored in HTTP_CLIENT/ORCHESTRATED)"
                },
                "serverPublicKey": {
                  "type": "string",
                  "description": "Base64url-encoded server public key (ORCHESTRATED mode phase 2 — completing exchange)"
                },
                "handshakeId": {
                  "type": "string",
                  "description": "Handshake session ID (ORCHESTRATED mode phase 2 — completing exchange)"
                },
                "cipherSuite": {
                  "type": "string",
                  "description": "Selected cipher suite (e.g. 'ECDH-P256-AES128-GCM-SHA256')"
                },
                "authMethod": {
                  "type": "string",
                  "description": "Selected auth method: 'HMAC-SHA256' or 'JWT'"
                },
                "clientId": {
                  "type": "string",
                  "description": "Client identifier"
                },
                "clientNonce": {
                  "type": "string",
                  "description": "Client-generated nonce"
                },
                "apiKey": {
                  "type": "string",
                  "description": "Pre-shared API key for bootstrap authentication (API_KEY/MUTUAL modes)"
                }
              },
              "required": ["cipherSuite", "authMethod", "clientId"]
            }""";

    private final HandshakeHttpClient httpClient;

    public NegotiateSessionTool(CryptoService cryptoService, HandshakeSessionStore sessionStore,
                                SecurityHandshakeProperties properties, HandshakeHttpClient httpClient) {
        super(new ToolDefinition(
                "security_negotiate_session",
                "Perform ECDH key exchange — generates keys, negotiates with the MCP server, and derives a shared secret.",
                ToolCatalog.SECTION_SECURITY,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), cryptoService, sessionStore, properties);
        this.httpClient = httpClient;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        return switch (mode()) {
            case LOCAL -> executeLocal(parameters);
            case HTTP_CLIENT -> executeHttpClient(parameters);
            case ORCHESTRATED -> executeOrchestrated(parameters);
        };
    }

    /**
     * LOCAL mode: acts as the server — accepts client public key, generates server key pair,
     * performs key agreement, stores everything in session.
     */
    private ToolResult executeLocal(Map<String, Object> parameters) throws Exception {
        String clientPublicKeyEncoded = requireParam(parameters, "clientPublicKey");
        String cipherSuite = requireParam(parameters, "cipherSuite");
        String authMethod = requireParam(parameters, "authMethod");
        String clientId = requireParam(parameters, "clientId");
        String clientNonce = optionalParam(parameters, "clientNonce", null);

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

        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "serverPublicKey": "%s",
                  "keyFingerprint": "%s",
                  "algorithm": "%s",
                  "serverNonce": "%s",
                  "sharedSecretEstablished": true
                }""",
                session.getHandshakeId(),
                cryptoService.encodePublicKey(serverKeyPair.getPublic()),
                fingerprint,
                agreementAlgorithm,
                session.getServerNonce()));
    }

    /**
     * HTTP_CLIENT mode: generates client key pair, sends public key to MCP server,
     * receives server public key, performs key agreement locally.
     */
    private ToolResult executeHttpClient(Map<String, Object> parameters) throws Exception {
        String cipherSuite = requireParam(parameters, "cipherSuite");
        String authMethod = requireParam(parameters, "authMethod");
        String clientId = requireParam(parameters, "clientId");
        String clientNonce = optionalParam(parameters, "clientNonce", cryptoService.generateNonce());

        boolean useX25519 = cipherSuite.contains("X25519");
        String keyAlgorithm = useX25519 ? "X25519" : "EC";
        String agreementAlgorithm = useX25519 ? "XDH" : "ECDH";

        // Generate client key pair
        KeyPair clientKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();
        String clientPublicKeyEncoded = cryptoService.encodePublicKey(clientKeyPair.getPublic());

        // Build negotiate payload with bootstrap credentials
        var payload = new java.util.HashMap<String, Object>();
        payload.put("clientPublicKey", clientPublicKeyEncoded);
        payload.put("cipherSuite", cipherSuite);
        payload.put("authMethod", authMethod);
        payload.put("clientId", clientId);
        payload.put("clientNonce", clientNonce);

        // Include bootstrap credentials from config or parameters
        String apiKey = optionalParam(parameters, "apiKey", properties.apiKey());
        if (apiKey != null) {
            payload.put("apiKey", apiKey);
        }

        // Send to MCP server
        String serverResponse = httpClient.post("/negotiate", payload);

        // Parse server response to get server public key and handshake ID
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var responseNode = mapper.readTree(serverResponse);
        String serverPublicKeyEncoded = responseNode.get("serverPublicKey").asText();
        String handshakeId = responseNode.get("handshakeId").asText();
        String serverNonce = responseNode.has("serverNonce") ? responseNode.get("serverNonce").asText() : null;

        // Perform key agreement locally
        PublicKey serverPublicKey = cryptoService.decodePublicKey(serverPublicKeyEncoded, keyAlgorithm);
        byte[] sharedSecret = cryptoService.keyAgreement(
                clientKeyPair.getPrivate(), serverPublicKey, agreementAlgorithm);
        String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);

        // Store in local session (keyed by the server's handshake ID)
        HandshakeSession session = new HandshakeSession(handshakeId);
        session.setSelectedCipherSuite(cipherSuite);
        session.setSelectedAuthMethod(authMethod);
        session.setClientId(clientId);
        session.setClientNonce(clientNonce);
        session.setServerNonce(serverNonce);
        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(fingerprint);
        session.setAlgorithm(agreementAlgorithm);
        sessionStore.put(handshakeId, session);

        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "serverPublicKey": "%s",
                  "keyFingerprint": "%s",
                  "algorithm": "%s",
                  "serverNonce": "%s",
                  "sharedSecretEstablished": true,
                  "mode": "HTTP_CLIENT"
                }""",
                handshakeId,
                serverPublicKeyEncoded,
                fingerprint,
                agreementAlgorithm,
                serverNonce != null ? serverNonce : ""));
    }

    /**
     * ORCHESTRATED mode phase 1: generates client key pair, returns public key for LLM.
     * Phase 2 (when serverPublicKey is provided): completes key agreement.
     */
    private ToolResult executeOrchestrated(Map<String, Object> parameters) throws Exception {
        String serverPublicKeyEncoded = optionalParam(parameters, "serverPublicKey", null);

        if (serverPublicKeyEncoded != null) {
            // Phase 2: complete key agreement with server's public key
            return completeOrchestratedExchange(parameters, serverPublicKeyEncoded);
        }

        // Phase 1: generate client key pair
        String cipherSuite = requireParam(parameters, "cipherSuite");
        String authMethod = requireParam(parameters, "authMethod");
        String clientId = requireParam(parameters, "clientId");
        String clientNonce = optionalParam(parameters, "clientNonce", cryptoService.generateNonce());

        boolean useX25519 = cipherSuite.contains("X25519");
        KeyPair clientKeyPair = useX25519
                ? cryptoService.generateX25519KeyPair()
                : cryptoService.generateP256KeyPair();
        String clientPublicKeyEncoded = cryptoService.encodePublicKey(clientKeyPair.getPublic());

        // Store the client key pair in a session for phase 2
        HandshakeSession session = sessionStore.create();
        session.setSelectedCipherSuite(cipherSuite);
        session.setSelectedAuthMethod(authMethod);
        session.setClientId(clientId);
        session.setClientNonce(clientNonce);
        session.setServerKeyPair(clientKeyPair); // reuse field to store CLIENT key pair
        session.setAlgorithm(useX25519 ? "XDH" : "ECDH");

        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "clientPublicKey": "%s",
                  "cipherSuite": "%s",
                  "authMethod": "%s",
                  "clientId": "%s",
                  "clientNonce": "%s",
                  "mode": "ORCHESTRATED",
                  "instructions": "Send this clientPublicKey to the MCP server's negotiate endpoint. Then call this tool again with the serverPublicKey from the response."
                }""",
                session.getHandshakeId(),
                clientPublicKeyEncoded,
                cipherSuite,
                authMethod,
                clientId,
                clientNonce));
    }

    private ToolResult completeOrchestratedExchange(Map<String, Object> parameters,
                                                     String serverPublicKeyEncoded) throws Exception {
        String handshakeId = requireParam(parameters, "handshakeId");
        HandshakeSession session = sessionStore.require(handshakeId);

        boolean useX25519 = session.getSelectedCipherSuite().contains("X25519");
        String keyAlgorithm = useX25519 ? "X25519" : "EC";

        PublicKey serverPublicKey = cryptoService.decodePublicKey(serverPublicKeyEncoded, keyAlgorithm);
        // serverKeyPair field is reused to store the client's key pair in ORCHESTRATED mode
        byte[] sharedSecret = cryptoService.keyAgreement(
                session.getServerKeyPair().getPrivate(), serverPublicKey, session.getAlgorithm());
        String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);

        session.setSharedSecret(sharedSecret);
        session.setKeyFingerprint(fingerprint);

        return new ToolResult.Success(String.format("""
                {
                  "handshakeId": "%s",
                  "keyFingerprint": "%s",
                  "algorithm": "%s",
                  "sharedSecretEstablished": true,
                  "mode": "ORCHESTRATED"
                }""",
                handshakeId,
                fingerprint,
                session.getAlgorithm()));
    }
}

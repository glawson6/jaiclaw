package io.jaiclaw.tools.security;

import java.util.List;

/**
 * Language-typed Java interface for the security handshake server protocol.
 * See {@code HANDSHAKE-PROTOCOL.md} for the language-agnostic specification.
 *
 * <p>Implementors provide the 5 handshake endpoints as typed methods.
 * The existing {@link SecurityHandshakeMcpProvider} implements this interface
 * and bridges between the MCP JSON layer and these typed methods.
 *
 * <p>All records use {@code String} for Base64url-encoded binary values
 * and JSON-compatible types only — no Spring or framework dependency.
 */
public interface HandshakeServerEndpoint {

    // ── Requests ──

    /**
     * Request for the negotiate endpoint.
     *
     * @param clientPublicKey Base64url-encoded X.509 DER public key
     * @param cipherSuite    selected cipher suite
     * @param authMethod     selected auth method (HMAC-SHA256 or JWT)
     * @param clientId       client identifier
     * @param clientNonce    optional client-generated nonce (for MUTUAL bootstrap)
     * @param apiKey         optional pre-shared API key (for API_KEY/MUTUAL bootstrap)
     * @param clientSignature optional client signature (for CLIENT_CERT/MUTUAL bootstrap)
     */
    record NegotiateRequest(
            String clientPublicKey,
            String cipherSuite,
            String authMethod,
            String clientId,
            String clientNonce,
            String apiKey,
            String clientSignature
    ) {}

    /**
     * Request for the challenge endpoint.
     *
     * @param handshakeId handshake session ID from negotiate
     */
    record ChallengeRequest(String handshakeId) {}

    /**
     * Request for the verify endpoint.
     *
     * @param handshakeId handshake session ID
     * @param method      verification method (HMAC-SHA256 or JWT)
     * @param credential  signed credential (HMAC signature or JWT)
     */
    record VerifyRequest(String handshakeId, String method, String credential) {}

    /**
     * Request for the establish endpoint.
     *
     * @param handshakeId handshake session ID
     * @param ttlSeconds  optional token TTL in seconds (null = server default)
     */
    record EstablishRequest(String handshakeId, Integer ttlSeconds) {}

    // ── Responses ──

    /**
     * Response from the capabilities endpoint.
     */
    record CapabilitiesResponse(
            List<String> cipherSuites,
            List<String> authMethods,
            List<String> keyExchangeAlgorithms,
            String bootstrapTrust,
            boolean serverSide
    ) {}

    /**
     * Response from the negotiate endpoint.
     */
    record NegotiateResponse(
            String handshakeId,
            String serverPublicKey,
            String keyFingerprint,
            String algorithm,
            String serverNonce,
            boolean sharedSecretEstablished,
            String serverSignature
    ) {}

    /**
     * Response from the challenge endpoint.
     */
    record ChallengeResponse(String handshakeId, String challenge, String authMethod) {}

    /**
     * Response from the verify endpoint.
     */
    record VerifyResponse(
            String handshakeId,
            String authMethod,
            boolean verified,
            String subject,
            String verificationDetails
    ) {}

    /**
     * Response from the establish endpoint.
     */
    record EstablishResponse(
            String handshakeId,
            String sessionToken,
            String cipherSuite,
            int expiresInSeconds,
            String subject,
            String summary
    ) {}

    // ── Endpoint methods ──

    /**
     * Returns the server's supported cipher suites, auth methods, and bootstrap trust level.
     */
    CapabilitiesResponse capabilities();

    /**
     * Validates bootstrap credentials, performs ECDH key exchange, and creates a handshake session.
     *
     * @throws IllegalArgumentException if a required field is missing
     * @throws SecurityException        if bootstrap validation fails
     */
    NegotiateResponse negotiate(NegotiateRequest request);

    /**
     * Issues a random nonce challenge for identity verification.
     *
     * @throws IllegalArgumentException if handshakeId is unknown
     * @throws IllegalStateException    if key exchange has not completed
     */
    ChallengeResponse challenge(ChallengeRequest request);

    /**
     * Verifies the client's HMAC response to the challenge.
     *
     * @throws IllegalArgumentException if handshakeId is unknown
     * @throws IllegalStateException    if no challenge has been issued or key exchange incomplete
     */
    VerifyResponse verify(VerifyRequest request);

    /**
     * Creates a JWT session token for the authenticated client.
     *
     * @throws IllegalArgumentException if handshakeId is unknown
     * @throws IllegalStateException    if identity has not been verified or key exchange incomplete
     */
    EstablishResponse establish(EstablishRequest request);
}

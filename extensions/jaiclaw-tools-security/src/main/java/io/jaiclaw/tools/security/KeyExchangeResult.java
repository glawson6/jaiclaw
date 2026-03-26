package io.jaiclaw.tools.security;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Phase 2 result — key exchange output placed on the Embabel blackboard.
 */
@JsonClassDescription("Result of ECDH key exchange with server public key and fingerprint")
public record KeyExchangeResult(
        @JsonProperty("handshakeId")
        @JsonPropertyDescription("The handshake session ID")
        String handshakeId,

        @JsonProperty("algorithm")
        @JsonPropertyDescription("Key exchange algorithm used (ECDH or XDH)")
        String algorithm,

        @JsonProperty("serverPublicKey")
        @JsonPropertyDescription("Base64url-encoded server public key")
        String serverPublicKey,

        @JsonProperty("sharedSecretEstablished")
        @JsonPropertyDescription("Whether the shared secret has been derived")
        boolean sharedSecretEstablished,

        @JsonProperty("keyFingerprint")
        @JsonPropertyDescription("SHA-256 fingerprint of the shared secret")
        String keyFingerprint
) {}

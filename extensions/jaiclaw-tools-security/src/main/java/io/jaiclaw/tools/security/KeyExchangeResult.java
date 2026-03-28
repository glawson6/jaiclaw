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
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String handshakeId;
        private String algorithm;
        private String serverPublicKey;
        private boolean sharedSecretEstablished;
        private String keyFingerprint;

        public Builder handshakeId(String handshakeId) { this.handshakeId = handshakeId; return this; }
        public Builder algorithm(String algorithm) { this.algorithm = algorithm; return this; }
        public Builder serverPublicKey(String serverPublicKey) { this.serverPublicKey = serverPublicKey; return this; }
        public Builder sharedSecretEstablished(boolean sharedSecretEstablished) { this.sharedSecretEstablished = sharedSecretEstablished; return this; }
        public Builder keyFingerprint(String keyFingerprint) { this.keyFingerprint = keyFingerprint; return this; }

        public KeyExchangeResult build() {
            return new KeyExchangeResult(
                    handshakeId, algorithm, serverPublicKey, sharedSecretEstablished, keyFingerprint);
        }
    }
}

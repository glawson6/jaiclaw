package io.jaiclaw.tools.security;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Input to the security handshake GOAP chain.
 * Placed on the Embabel blackboard to initiate the handshake.
 */
@JsonClassDescription("Client request to initiate a security handshake")
public record HandshakeRequest(
        @JsonProperty("clientId")
        @JsonPropertyDescription("Unique identifier for the client")
        String clientId,

        @JsonProperty("preferredCipherSuites")
        @JsonPropertyDescription("Client's preferred cipher suites in priority order")
        List<String> preferredCipherSuites,

        @JsonProperty("preferredAuthMethods")
        @JsonPropertyDescription("Client's preferred authentication methods")
        List<String> preferredAuthMethods,

        @JsonProperty("clientNonce")
        @JsonPropertyDescription("Client-generated nonce for freshness")
        String clientNonce
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String clientId;
        private List<String> preferredCipherSuites;
        private List<String> preferredAuthMethods;
        private String clientNonce;

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder preferredCipherSuites(List<String> preferredCipherSuites) { this.preferredCipherSuites = preferredCipherSuites; return this; }
        public Builder preferredAuthMethods(List<String> preferredAuthMethods) { this.preferredAuthMethods = preferredAuthMethods; return this; }
        public Builder clientNonce(String clientNonce) { this.clientNonce = clientNonce; return this; }

        public HandshakeRequest build() {
            return new HandshakeRequest(
                    clientId, preferredCipherSuites, preferredAuthMethods, clientNonce);
        }
    }
}

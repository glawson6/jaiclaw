package io.jaiclaw.tools.security;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Phase 4 / goal condition — the completed security handshake.
 * Placed on the Embabel blackboard when the session is established.
 */
@JsonClassDescription("Completed security handshake with session token and parameters")
public record SessionEstablished(
        @JsonProperty("handshakeId")
        @JsonPropertyDescription("The handshake session ID")
        String handshakeId,

        @JsonProperty("sessionToken")
        @JsonPropertyDescription("Signed JWT session token")
        String sessionToken,

        @JsonProperty("cipherSuite")
        @JsonPropertyDescription("The negotiated cipher suite")
        String cipherSuite,

        @JsonProperty("expiresInSeconds")
        @JsonPropertyDescription("Token TTL in seconds")
        int expiresInSeconds,

        @JsonProperty("summary")
        @JsonPropertyDescription("Human-readable summary of the established session")
        String summary
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String handshakeId;
        private String sessionToken;
        private String cipherSuite;
        private int expiresInSeconds;
        private String summary;

        public Builder handshakeId(String handshakeId) { this.handshakeId = handshakeId; return this; }
        public Builder sessionToken(String sessionToken) { this.sessionToken = sessionToken; return this; }
        public Builder cipherSuite(String cipherSuite) { this.cipherSuite = cipherSuite; return this; }
        public Builder expiresInSeconds(int expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }

        public SessionEstablished build() {
            return new SessionEstablished(
                    handshakeId, sessionToken, cipherSuite, expiresInSeconds, summary);
        }
    }
}

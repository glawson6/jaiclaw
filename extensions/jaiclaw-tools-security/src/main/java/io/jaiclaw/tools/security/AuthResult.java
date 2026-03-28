package io.jaiclaw.tools.security;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Phase 3 result — identity verification outcome placed on the Embabel blackboard.
 */
@JsonClassDescription("Result of identity verification via challenge-response")
public record AuthResult(
        @JsonProperty("handshakeId")
        @JsonPropertyDescription("The handshake session ID")
        String handshakeId,

        @JsonProperty("authMethod")
        @JsonPropertyDescription("Authentication method used (HMAC-SHA256 or JWT)")
        String authMethod,

        @JsonProperty("verified")
        @JsonPropertyDescription("Whether identity was successfully verified")
        boolean verified,

        @JsonProperty("subject")
        @JsonPropertyDescription("The verified identity subject (null if verification failed)")
        String subject,

        @JsonProperty("verificationDetails")
        @JsonPropertyDescription("Human-readable verification details")
        String verificationDetails
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String handshakeId;
        private String authMethod;
        private boolean verified;
        private String subject;
        private String verificationDetails;

        public Builder handshakeId(String handshakeId) { this.handshakeId = handshakeId; return this; }
        public Builder authMethod(String authMethod) { this.authMethod = authMethod; return this; }
        public Builder verified(boolean verified) { this.verified = verified; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder verificationDetails(String verificationDetails) { this.verificationDetails = verificationDetails; return this; }

        public AuthResult build() {
            return new AuthResult(handshakeId, authMethod, verified, subject, verificationDetails);
        }
    }
}

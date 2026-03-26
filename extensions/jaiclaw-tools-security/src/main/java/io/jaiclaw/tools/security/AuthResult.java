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
) {}

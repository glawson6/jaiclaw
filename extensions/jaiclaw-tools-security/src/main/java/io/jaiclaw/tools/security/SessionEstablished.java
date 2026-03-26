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
) {}

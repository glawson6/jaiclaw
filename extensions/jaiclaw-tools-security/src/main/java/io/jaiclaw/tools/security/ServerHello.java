package io.jaiclaw.tools.security;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Phase 1 result — server's response to the handshake request with
 * selected parameters and capabilities.
 */
@JsonClassDescription("Server hello with selected cipher suite, auth method, and capabilities")
public record ServerHello(
        @JsonProperty("handshakeId")
        @JsonPropertyDescription("Unique identifier for this handshake session")
        String handshakeId,

        @JsonProperty("selectedCipherSuite")
        @JsonPropertyDescription("The cipher suite selected by the server")
        String selectedCipherSuite,

        @JsonProperty("selectedAuthMethod")
        @JsonPropertyDescription("The authentication method selected by the server")
        String selectedAuthMethod,

        @JsonProperty("serverNonce")
        @JsonPropertyDescription("Server-generated nonce for freshness")
        String serverNonce,

        @JsonProperty("serverCapabilities")
        @JsonPropertyDescription("Full list of server capabilities")
        List<String> serverCapabilities
) {}

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
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String handshakeId;
        private String selectedCipherSuite;
        private String selectedAuthMethod;
        private String serverNonce;
        private List<String> serverCapabilities;

        public Builder handshakeId(String handshakeId) { this.handshakeId = handshakeId; return this; }
        public Builder selectedCipherSuite(String selectedCipherSuite) { this.selectedCipherSuite = selectedCipherSuite; return this; }
        public Builder selectedAuthMethod(String selectedAuthMethod) { this.selectedAuthMethod = selectedAuthMethod; return this; }
        public Builder serverNonce(String serverNonce) { this.serverNonce = serverNonce; return this; }
        public Builder serverCapabilities(List<String> serverCapabilities) { this.serverCapabilities = serverCapabilities; return this; }

        public ServerHello build() {
            return new ServerHello(
                    handshakeId, selectedCipherSuite, selectedAuthMethod, serverNonce, serverCapabilities);
        }
    }
}

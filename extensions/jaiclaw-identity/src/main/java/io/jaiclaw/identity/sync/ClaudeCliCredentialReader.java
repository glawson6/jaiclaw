package io.jaiclaw.identity.sync;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.auth.AuthProfileConstants;
import io.jaiclaw.core.auth.AuthProfileCredential;
import io.jaiclaw.core.auth.OAuthCredential;
import io.jaiclaw.core.auth.TokenCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads Claude CLI credentials from macOS Keychain or {@code ~/.claude/.credentials.json}.
 */
public class ClaudeCliCredentialReader {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliCredentialReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeychainReader keychainReader;

    public ClaudeCliCredentialReader(KeychainReader keychainReader) {
        this.keychainReader = keychainReader;
    }

    /** Read Claude CLI credentials. Tries Keychain first (macOS), then file. */
    public Optional<AuthProfileCredential> read() {
        // Try macOS Keychain first
        Optional<String> keychainValue = keychainReader.readGenericPassword(
                AuthProfileConstants.CLAUDE_KEYCHAIN_SERVICE,
                AuthProfileConstants.CLAUDE_KEYCHAIN_ACCOUNT
        );
        if (keychainValue.isPresent()) {
            Optional<AuthProfileCredential> cred = parseClaudeCredentials(keychainValue.get());
            if (cred.isPresent()) return cred;
        }

        // Fall back to file
        Path credPath = Path.of(System.getProperty("user.home"))
                .resolve(AuthProfileConstants.CLAUDE_CLI_CREDS_PATH);
        if (Files.exists(credPath)) {
            try {
                String content = Files.readString(credPath);
                return parseClaudeCredentials(content);
            } catch (IOException e) {
                log.debug("Failed to read Claude CLI credentials file: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    private Optional<AuthProfileCredential> parseClaudeCredentials(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode oauth = root.get("claudeAiOauth");
            if (oauth == null) return Optional.empty();

            String accessToken = textOrNull(oauth, "accessToken");
            String refreshToken = textOrNull(oauth, "refreshToken");
            Long expiresAt = oauth.has("expiresAt") && oauth.get("expiresAt").isNumber()
                    ? oauth.get("expiresAt").asLong() : null;

            if (accessToken == null || accessToken.isBlank()) return Optional.empty();

            if (refreshToken != null && !refreshToken.isBlank()) {
                // Full OAuth credential
                long expires = expiresAt != null ? expiresAt : System.currentTimeMillis() + 3600_000;
                return Optional.of(new OAuthCredential(
                        "anthropic", accessToken, refreshToken, expires,
                        null, null, null, null, null));
            } else {
                // Token-only (no refresh)
                return Optional.of(new TokenCredential(
                        "anthropic", accessToken, null, expiresAt, null));
            }
        } catch (Exception e) {
            log.debug("Failed to parse Claude CLI credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}

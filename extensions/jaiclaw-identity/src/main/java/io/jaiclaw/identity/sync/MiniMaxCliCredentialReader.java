package io.jaiclaw.identity.sync;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.auth.AuthProfileConstants;
import io.jaiclaw.core.auth.OAuthCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads MiniMax CLI credentials from {@code ~/.minimax/oauth_creds.json}.
 */
public class MiniMaxCliCredentialReader {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxCliCredentialReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Read MiniMax CLI credentials. */
    public Optional<OAuthCredential> read() {
        Path credPath = Path.of(System.getProperty("user.home"))
                .resolve(AuthProfileConstants.MINIMAX_CLI_CREDS_PATH);
        if (!Files.exists(credPath)) return Optional.empty();

        try {
            String content = Files.readString(credPath);
            return parseCredentials(content);
        } catch (IOException e) {
            log.debug("Failed to read MiniMax CLI credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<OAuthCredential> parseCredentials(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String accessToken = textOrNull(root, "access_token");
            String refreshToken = textOrNull(root, "refresh_token");
            long expiryDate = root.has("expiry_date") ? root.get("expiry_date").asLong(0) : 0;

            if (accessToken == null || accessToken.isBlank()) return Optional.empty();

            return Optional.of(new OAuthCredential(
                    "minimax-portal", accessToken, refreshToken, expiryDate,
                    null, null, null, null, null));
        } catch (Exception e) {
            log.debug("Failed to parse MiniMax CLI credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}

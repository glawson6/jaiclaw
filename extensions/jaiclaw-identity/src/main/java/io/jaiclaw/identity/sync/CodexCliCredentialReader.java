package io.jaiclaw.identity.sync;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.auth.AuthProfileConstants;
import io.jaiclaw.core.auth.OAuthCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Reads Codex CLI (OpenAI) credentials from macOS Keychain or {@code ~/.codex/auth.json}.
 */
public class CodexCliCredentialReader {

    private static final Logger log = LoggerFactory.getLogger(CodexCliCredentialReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    private final KeychainReader keychainReader;

    public CodexCliCredentialReader(KeychainReader keychainReader) {
        this.keychainReader = keychainReader;
    }

    /** Read Codex CLI credentials. Tries Keychain first (macOS), then file. */
    public Optional<OAuthCredential> read() {
        Path codexHome = resolveCodexHome();

        // Try macOS Keychain first
        String keychainAccount = computeKeychainAccount(codexHome);
        Optional<String> keychainValue = keychainReader.readGenericPassword(
                AuthProfileConstants.CODEX_KEYCHAIN_SERVICE, keychainAccount);
        if (keychainValue.isPresent()) {
            Optional<OAuthCredential> cred = parseCodexCredentials(keychainValue.get(), null);
            if (cred.isPresent()) return cred;
        }

        // Fall back to file
        Path authFile = codexHome.resolve(AuthProfileConstants.CODEX_CLI_AUTH_FILENAME);
        if (Files.exists(authFile)) {
            try {
                String content = Files.readString(authFile);
                long fileMtime = Files.getLastModifiedTime(authFile).toMillis();
                return parseCodexCredentials(content, fileMtime);
            } catch (IOException e) {
                log.debug("Failed to read Codex CLI auth file: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    private Optional<OAuthCredential> parseCodexCredentials(String json, Long fileMtime) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode tokens = root.get("tokens");
            if (tokens == null) return Optional.empty();

            String accessToken = textOrNull(tokens, "access_token");
            String refreshToken = textOrNull(tokens, "refresh_token");
            String accountId = textOrNull(tokens, "account_id");

            if (accessToken == null || accessToken.isBlank()) return Optional.empty();

            // Compute expiry: last_refresh + 1 hour, or file mtime + 1 hour
            long expires;
            if (root.has("last_refresh") && root.get("last_refresh").isNumber()) {
                expires = root.get("last_refresh").asLong() + ONE_HOUR_MS;
            } else if (fileMtime != null) {
                expires = fileMtime + ONE_HOUR_MS;
            } else {
                expires = System.currentTimeMillis() + ONE_HOUR_MS;
            }

            return Optional.of(new OAuthCredential(
                    "openai-codex", accessToken, refreshToken, expires,
                    null, null, accountId, null, null));
        } catch (Exception e) {
            log.debug("Failed to parse Codex CLI credentials: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Path resolveCodexHome() {
        String codexHome = System.getenv("CODEX_HOME");
        if (codexHome != null && !codexHome.isBlank()) {
            return Path.of(codexHome);
        }
        return Path.of(System.getProperty("user.home"), ".codex");
    }

    /**
     * Compute the Keychain account: {@code cli|<first 16 hex chars of SHA-256 of codexHome>}.
     */
    private String computeKeychainAccount(Path codexHome) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codexHome.toString().getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return "cli|" + hex.substring(0, 16);
        } catch (Exception e) {
            return "cli|unknown";
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}

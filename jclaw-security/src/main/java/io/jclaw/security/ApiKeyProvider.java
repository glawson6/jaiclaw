package io.jclaw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Manages the API key lifecycle: resolves an explicit key, reads from a file, or
 * auto-generates one and persists it to disk.
 * <p>
 * Key format: {@code jclaw_ak_} followed by 32 hex characters (48 chars total).
 */
public class ApiKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyProvider.class);
    private static final String KEY_PREFIX = "jclaw_ak_";

    private final String resolvedKey;
    private final String source;

    public ApiKeyProvider(String explicitKey, String keyFilePath) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            this.resolvedKey = explicitKey.trim();
            this.source = "property";
            log.debug("API key resolved from explicit property");
        } else {
            Path keyFile = Path.of(keyFilePath);
            String fileKey = readKeyFromFile(keyFile);
            if (fileKey != null) {
                this.resolvedKey = fileKey;
                this.source = "file";
                log.debug("API key resolved from file: {}", keyFile);
            } else {
                this.resolvedKey = generateKey();
                this.source = "generated";
                writeKeyToFile(keyFile, this.resolvedKey);
                log.debug("API key auto-generated and saved to: {}", keyFile);
            }
        }
    }

    public String getResolvedKey() {
        return resolvedKey;
    }

    public String getSource() {
        return source;
    }

    /**
     * Returns the key with the first portion masked for safe display in logs.
     * Shows only the last 8 characters.
     */
    public String getMaskedKey() {
        if (resolvedKey.length() <= 8) {
            return "********";
        }
        return "*".repeat(resolvedKey.length() - 8) + resolvedKey.substring(resolvedKey.length() - 8);
    }

    static String generateKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(KEY_PREFIX);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String readKeyFromFile(Path keyFile) {
        if (!Files.isRegularFile(keyFile)) {
            return null;
        }
        try {
            String content = Files.readString(keyFile).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.warn("Could not read API key file {}: {}", keyFile, e.getMessage());
            return null;
        }
    }

    private static void writeKeyToFile(Path keyFile, String key) {
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, key + System.lineSeparator());
        } catch (IOException e) {
            log.warn("Could not write API key to {}: {}", keyFile, e.getMessage());
        }
    }
}

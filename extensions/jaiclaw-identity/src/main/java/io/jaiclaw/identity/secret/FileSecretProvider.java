package io.jaiclaw.identity.secret;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Resolves secrets from files (JSON pointer or single-value mode).
 */
public class FileSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(FileSecretProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Resolve a secret from a file.
     *
     * @param id     JSON pointer path (e.g. "/providers/openai/apiKey") or ignored for singleValue
     * @param config provider config with file path and mode
     * @return the secret value
     */
    public String resolve(String id, SecretProviderConfig config) {
        if (config.path() == null || config.path().isBlank()) {
            throw new SecretRefResolver.SecretResolutionException(
                    "File secret provider has no path configured");
        }

        Path filePath = Path.of(config.path());
        validateFilePath(filePath, config);

        try {
            long fileSize = Files.size(filePath);
            if (fileSize > config.maxBytes()) {
                throw new SecretRefResolver.SecretResolutionException(
                        "File exceeds max size (" + fileSize + " > " + config.maxBytes() + "): " + filePath);
            }

            String mode = config.mode() != null ? config.mode() : SecretProviderConfig.MODE_JSON;

            if (SecretProviderConfig.MODE_SINGLE_VALUE.equals(mode)) {
                return Files.readString(filePath).trim();
            }

            // JSON mode: use id as JSON pointer
            byte[] content = Files.readAllBytes(filePath);
            JsonNode root = MAPPER.readTree(content);
            JsonNode target = root.at(id);
            if (target.isMissingNode() || target.isNull()) {
                throw new SecretRefResolver.SecretResolutionException(
                        "JSON pointer '" + id + "' not found in file: " + filePath);
            }
            return target.isTextual() ? target.asText() : target.toString();
        } catch (IOException e) {
            throw new SecretRefResolver.SecretResolutionException(
                    "Failed to read secret file: " + filePath, e);
        }
    }

    private void validateFilePath(Path filePath, SecretProviderConfig config) {
        if (!filePath.isAbsolute()) {
            throw new SecretRefResolver.SecretResolutionException(
                    "File path must be absolute: " + filePath);
        }
        if (!Files.exists(filePath)) {
            throw new SecretRefResolver.SecretResolutionException(
                    "Secret file does not exist: " + filePath);
        }
        if (Files.isDirectory(filePath)) {
            throw new SecretRefResolver.SecretResolutionException(
                    "Path is a directory, not a file: " + filePath);
        }

        // Check symlink (unless allowed)
        if (!config.allowSymlinkPath()) {
            try {
                if (Files.isSymbolicLink(filePath)) {
                    throw new SecretRefResolver.SecretResolutionException(
                            "Secret file is a symlink (set allowSymlinkPath=true to allow): " + filePath);
                }
            } catch (SecurityException e) {
                log.debug("Cannot check symlink status: {}", filePath);
            }
        }

        // Check permissions on POSIX systems
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(filePath, LinkOption.NOFOLLOW_LINKS);
            if (perms.contains(PosixFilePermission.OTHERS_READ)
                    || perms.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new SecretRefResolver.SecretResolutionException(
                        "Secret file is world-readable or world-writable: " + filePath);
            }
        } catch (UnsupportedOperationException e) {
            // Not a POSIX filesystem — skip permission check
        } catch (IOException e) {
            log.debug("Cannot check file permissions: {}", filePath);
        }
    }
}

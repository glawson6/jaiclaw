package io.jaiclaw.learning.ledger;

import io.jaiclaw.core.api.Experimental;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Content-addressed storage for skill bodies, so a rollback can restore exact
 * prior bytes.
 *
 * <p>Files are named by the SHA-256 of their content, which makes writes
 * idempotent and deduplicates automatically: applying the same patch twice, or
 * two skills sharing a body, costs one blob.
 *
 * <p>Blobs are never deleted by this class. A ledger whose blobs can vanish is a
 * ledger that cannot roll back, and disk is cheaper than an unrecoverable skill.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class BlobStore {

    private final Path root;

    public BlobStore(Path root) {
        this.root = root;
    }

    /**
     * Stores content and returns its hash. Idempotent — storing identical content
     * twice writes once.
     */
    public String put(String content) {
        String hash = sha256(content == null ? "" : content);
        Path target = pathFor(hash);
        if (Files.exists(target)) return hash;
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, content == null ? "" : content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return hash;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store blob " + hash, e);
        }
    }

    /** Retrieves content by hash, or empty if the blob is missing. */
    public Optional<String> get(String hash) {
        if (hash == null || hash.isBlank()) return Optional.empty();
        Path file = pathFor(hash);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public boolean contains(String hash) {
        return hash != null && Files.exists(pathFor(hash));
    }

    /** Fans out on the first two hex characters, so one directory never holds every blob. */
    private Path pathFor(String hash) {
        return root.resolve(hash.substring(0, 2)).resolve(hash);
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

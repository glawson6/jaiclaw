package io.jaiclaw.secrets.onepassword;

import io.jaiclaw.core.api.Stable;
import io.jaiclaw.core.secrets.SecretReference;
import io.jaiclaw.core.secrets.SecretsProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link SecretsProvider} backed by the {@code op} command-line tool.
 *
 * <p>Each lookup shells out to:
 * <pre>{@code
 *   op read op://<vault>/<item>/<field>
 * }</pre>
 *
 * <p>The {@code key} argument to {@link #get(String)} is interpreted
 * as the {@code <item>/<field>} portion (vault is config). If the key
 * already starts with {@code op://}, it's treated as a full
 * {@link SecretReference} and the vault from config is ignored.
 *
 * <p>Authentication is supplied via:
 * <ul>
 *   <li>{@code OP_SERVICE_ACCOUNT_TOKEN} env var for non-interactive use
 *       (CI, K8s nodes with the token mounted as a Secret).</li>
 *   <li>The user's interactive {@code op signin} session locally.</li>
 * </ul>
 *
 * <p>This provider does NOT implement {@link #getAll(String)} — the
 * {@code op} CLI doesn't expose a way to enumerate items by prefix
 * without a more complex multi-call dance. Returns an empty map.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public final class OnePasswordSecretsProvider implements SecretsProvider {

    private static final Logger log = LoggerFactory.getLogger(OnePasswordSecretsProvider.class);

    private final String opBinary;
    private final String vault;
    @Nullable
    private final String serviceAccountToken;
    private final Duration timeout;

    public OnePasswordSecretsProvider(Config config) {
        this.opBinary = Objects.requireNonNull(config.opBinary, "opBinary");
        this.vault = Objects.requireNonNull(config.vault, "vault");
        this.serviceAccountToken = config.serviceAccountToken;
        this.timeout = config.timeout != null ? config.timeout : Duration.ofSeconds(10);
        if (vault.isBlank()) {
            throw new IllegalArgumentException("vault must not be blank");
        }
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key");
        String reference = key.startsWith("op://")
                ? key
                : "op://" + vault + "/" + key;
        return readSecret(reference);
    }

    @Override
    public Map<String, String> getAll(String prefix) {
        // The op CLI doesn't expose efficient prefix enumeration.
        // Callers needing bulk reads should make per-key get() calls.
        log.debug("OnePasswordSecretsProvider.getAll({}) — not supported, returning empty", prefix);
        return Map.of();
    }

    @Override
    public String name() {
        return "onepassword";
    }

    /** Read a single secret. Package-private for testing. */
    Optional<String> readSecret(String reference) {
        ProcessBuilder pb = processBuilder(reference);
        Process process = null;
        try {
            process = pb.start();
            // Wait for the process FIRST. Reading stdout via the JDK
            // BufferedReader blocks until EOF, which would defeat any
            // timeout if we read before waiting.
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new SecretsProviderException(
                        "op CLI timed out after " + timeout + " for " + reference);
            }
            int exit = process.exitValue();
            String stdout = readStream(process.getInputStream());
            if (exit == 0) {
                return Optional.of(stdout.stripTrailing());
            }
            // Non-zero exit. The op CLI returns 1 for "item not found" as
            // well as other errors; distinguish by reading stderr.
            String stderr = readStream(process.getErrorStream());
            if (isNotFound(stderr)) {
                return Optional.empty();
            }
            throw new SecretsProviderException(
                    "op CLI exited " + exit + " for " + reference + ": " + stderr);
        } catch (IOException e) {
            throw new SecretsProviderException(
                    "Failed to invoke op CLI for " + reference, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new SecretsProviderException(
                    "Interrupted while reading " + reference, e);
        }
    }

    /**
     * Recognize known "item not present" error patterns from the op CLI.
     * Real-world error texts include:
     * <ul>
     *   <li>{@code "foo" isn't an item in the "Vault" vault}</li>
     *   <li>{@code item "foo" not found}</li>
     *   <li>{@code field "bar" doesn't exist on item "foo"}</li>
     * </ul>
     */
    private static boolean isNotFound(String stderr) {
        return stderr.contains("not found")
                || stderr.contains("doesn't exist")
                || stderr.contains("isn't an item");
    }

    /** Build the ProcessBuilder. Package-private for testing. */
    ProcessBuilder processBuilder(String reference) {
        ProcessBuilder pb = new ProcessBuilder(opBinary, "read", reference);
        if (serviceAccountToken != null) {
            pb.environment().put("OP_SERVICE_ACCOUNT_TOKEN", serviceAccountToken);
        }
        return pb;
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    /** Provider configuration. */
    public record Config(
            String opBinary,
            String vault,
            @Nullable String serviceAccountToken,
            @Nullable Duration timeout) {

        /** Default config: looks for {@code op} on PATH, 10-second timeout. */
        public static Config of(String vault) {
            return new Config("op", vault, null, Duration.ofSeconds(10));
        }
    }

    /** Thrown when the underlying {@code op} CLI invocation fails. */
    public static final class SecretsProviderException extends RuntimeException {
        SecretsProviderException(String message) { super(message); }
        SecretsProviderException(String message, Throwable cause) { super(message, cause); }
    }
}

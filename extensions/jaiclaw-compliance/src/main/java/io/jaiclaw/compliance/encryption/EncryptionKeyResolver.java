package io.jaiclaw.compliance.encryption;

import io.jaiclaw.core.secrets.SecretResolution;
import io.jaiclaw.core.secrets.SecretsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Resolves the 32-byte AES key that backs encryption at rest.
 *
 * <h2>Why this exists</h2>
 * {@link io.jaiclaw.core.encryption.FieldEncryptor} deliberately does not
 * resolve its own key — its contract puts that on the caller. Until 1.3.0 there
 * was no caller: no {@code FieldEncryptor} bean existed anywhere in the
 * framework, so encryption at rest was reachable only by an adopter writing
 * their own wiring. This class closes that gap for the {@code soc2} compliance
 * profile.
 *
 * <h2>Where the key comes from</h2>
 * Two sources, in order:
 * <ol>
 *   <li>{@code jaiclaw.compliance.encryption.key} read from the environment,
 *       which also covers {@code ${ENV_VAR}} placeholders and anything a
 *       {@code SecretsEnvironmentPostProcessor} has already injected;</li>
 *   <li>a {@link SecretsResolver}, when one is present, under the same logical
 *       key — so 1Password / Vault / file backends work without a second
 *       property.</li>
 * </ol>
 *
 * <h2>Encoding</h2>
 * AES-256 needs <strong>exactly</strong> 32 bytes. A passphrase is not a key, so
 * the value is decoded rather than taken literally: base64 first, hex second.
 * {@code String.getBytes()} is deliberately <em>not</em> a fallback — it only
 * yields 32 bytes for a 32-character ASCII string, which is a coincidence rather
 * than a contract, and silently produces a weak key otherwise. (An older
 * snippet in {@code docs/MIGRATION-0.9.3.md} shows exactly that mistake.)
 *
 * <p>Every failure is reported by throwing, never by returning a fallback key.
 * A deployment that believes it encrypts but does not is worse off than one that
 * refuses to start.
 *
 * <p>1.3.0.
 */
public final class EncryptionKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(EncryptionKeyResolver.class);

    /** Property and secrets-key under which the AES key is looked up. */
    public static final String KEY_PROPERTY = "jaiclaw.compliance.encryption.key";

    /** AES-256 key length. Not configurable — {@code AesGcmFieldEncryptor} requires it. */
    private static final int KEY_BYTES = 32;

    private EncryptionKeyResolver() {}

    /**
     * Resolve and decode the key.
     *
     * @param rawFromEnvironment value of {@link #KEY_PROPERTY} from the
     *                           environment, or null when unset
     * @param secretsResolver    optional secrets chain, consulted when the
     *                           property is absent
     * @return the decoded 32-byte key
     * @throws IllegalStateException when no key is available, or when the value
     *                               present cannot be decoded to exactly 32 bytes
     */
    public static byte[] resolve(String rawFromEnvironment, SecretsResolver secretsResolver) {
        String raw = rawFromEnvironment;
        String source = "property " + KEY_PROPERTY;

        if (isBlank(raw) && secretsResolver != null) {
            SecretResolution resolution = secretsResolver.resolve(KEY_PROPERTY);
            if (resolution instanceof SecretResolution.Resolved resolved) {
                raw = resolved.value();
                source = "secrets provider '" + resolved.providerName() + "'";
            } else if (resolution instanceof SecretResolution.ProviderError err) {
                // Surface the provider failure rather than falling through to
                // "no key configured", which would misdescribe the cause.
                throw new IllegalStateException(
                        "Encryption is enabled but the secrets provider '" + err.providerName()
                                + "' failed resolving '" + KEY_PROPERTY + "'.", err.cause());
            }
        }

        if (isBlank(raw)) {
            throw new IllegalStateException(
                    "Encryption at rest is enabled but no key is configured. Set "
                            + KEY_PROPERTY + " to a base64- or hex-encoded 32-byte key, or "
                            + "supply it through a SecretsProvider under the same key. "
                            + "Generate one with: openssl rand -base64 32");
        }

        byte[] decoded = decode(raw.trim());
        if (decoded == null) {
            throw new IllegalStateException(
                    "Encryption key from " + source + " is not valid base64 or hex. AES-256 "
                            + "requires exactly " + KEY_BYTES + " bytes of key material; a "
                            + "passphrase is not a key. Generate one with: openssl rand -base64 32");
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "Encryption key from " + source + " decoded to " + decoded.length
                            + " bytes; AES-256 requires exactly " + KEY_BYTES + ". Generate one "
                            + "with: openssl rand -base64 32");
        }

        // Never log the key, its prefix, or its length-in-characters — only the
        // source, which is what an operator needs to debug a misconfiguration.
        log.info("Encryption at rest enabled — {}-byte AES key resolved from {}",
                KEY_BYTES, source);
        return decoded;
    }

    /**
     * Hex first, then base64 — see the class Javadoc for why the order is not
     * arbitrary. Returns null when neither decodes, leaving the caller to raise
     * a message naming both accepted encodings.
     */
    private static byte[] decode(String value) {
        // Hex is the stricter alphabet and is unambiguous at the key length we
        // require, so it goes first. A 64-char hex string would otherwise be
        // mis-read as 48 bytes of base64.
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException ignored) {
            // not hex — fall through to base64
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Whether the key is resolvable at all, for pre-flight checks that must not throw. */
    public static Optional<byte[]> tryResolve(String rawFromEnvironment,
                                              SecretsResolver secretsResolver) {
        try {
            return Optional.of(resolve(rawFromEnvironment, secretsResolver));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

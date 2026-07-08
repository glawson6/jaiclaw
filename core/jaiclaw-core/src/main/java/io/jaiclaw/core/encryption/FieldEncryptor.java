package io.jaiclaw.core.encryption;

import io.jaiclaw.core.api.Stable;

/**
 * T2-4 — SPI for encrypting a single field (typically the JSON payload of a
 * transcript, memory row, or audit event) at rest.
 *
 * <p>Reference impl is {@link AesGcmFieldEncryptor} — AES-GCM 256 with a
 * random 12-byte nonce per encrypt call. Ciphertext format is
 * {@code base64(nonce || tag || ciphertext)}.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@code encrypt} MUST use authenticated encryption — GCM, ChaCha20-Poly1305,
 *       or equivalent. Ciphertexts that don't authenticate MUST be rejected on
 *       decrypt with a checked exception.</li>
 *   <li>{@code encrypt(null)} returns null; {@code decrypt(null)} returns null.
 *       This makes it safe to decorate stores whose payloads are sometimes
 *       optional without a chain of null-checks at the call site.</li>
 *   <li>Key material is sourced from a {@code SecretsProvider} by the caller,
 *       not resolved inside the encryptor. Losing the key means losing the
 *       ciphertext — deployers MUST maintain a backup-encryption-key rotation
 *       plan (see the plan's risk callout #2).</li>
 * </ul>
 */
@Stable
public interface FieldEncryptor {

    /**
     * Encrypt {@code plaintext} using the configured key.
     *
     * @param plaintext the raw UTF-8 string to encrypt (nullable)
     * @return {@code base64(nonce || tag || ciphertext)}, or null when
     *         {@code plaintext == null}
     */
    String encrypt(String plaintext);

    /**
     * Decrypt {@code ciphertext} produced by a previous {@link #encrypt} call
     * using the same key.
     *
     * @param ciphertext base64 blob produced by {@code encrypt} (nullable)
     * @return the plaintext, or null when {@code ciphertext == null}
     * @throws EncryptionException on authentication failure or malformed input
     */
    String decrypt(String ciphertext);

    /**
     * Raised when decryption fails — authentication tag mismatch, malformed
     * base64, wrong key, or truncated blob. The message intentionally does not
     * expose the reason so an attacker can't distinguish "wrong tag" from
     * "corrupt data" from timing.
     */
    class EncryptionException extends RuntimeException {
        public EncryptionException(String message) { super(message); }
        public EncryptionException(String message, Throwable cause) { super(message, cause); }
    }
}

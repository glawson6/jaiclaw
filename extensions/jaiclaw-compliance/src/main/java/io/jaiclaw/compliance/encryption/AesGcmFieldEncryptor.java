package io.jaiclaw.compliance.encryption;

import io.jaiclaw.core.encryption.FieldEncryptor;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * T2-4 reference {@link FieldEncryptor} — AES-GCM 256 with per-call random
 * 12-byte nonce. Ciphertext format: {@code base64(nonce || tag_and_ciphertext)}.
 *
 * <p>The Cipher instance is not shared — GCM is not thread-safe on the same
 * key/nonce pair, and reusing a nonce with the same key is catastrophic
 * (nonce-reuse leak). A fresh {@code SecureRandom} nonce per call is the
 * only safe pattern.
 *
 * <p>Adopters MUST source the 32-byte key from a real secrets store (env,
 * file, 1Password, vault) via a {@code SecretsProvider}, not hard-code it.
 */
public class AesGcmFieldEncryptor implements FieldEncryptor {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";

    private final SecretKeySpec key;
    private final SecureRandom random;

    /**
     * @param key32Bytes the raw 32-byte (256-bit) key material
     * @throws IllegalArgumentException if the key isn't 32 bytes
     */
    public AesGcmFieldEncryptor(byte[] key32Bytes) {
        if (key32Bytes == null || key32Bytes.length != 32) {
            throw new IllegalArgumentException("AES-GCM key must be exactly 32 bytes");
        }
        this.key = new SecretKeySpec(key32Bytes, KEY_ALGO);
        this.random = new SecureRandom();
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new EncryptionException("encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] blob = Base64.getDecoder().decode(ciphertext);
            if (blob.length < NONCE_BYTES + TAG_BITS / 8) {
                throw new EncryptionException("ciphertext too short");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(blob, 0, nonce, 0, NONCE_BYTES);
            byte[] ct = new byte[blob.length - NONCE_BYTES];
            System.arraycopy(blob, NONCE_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (EncryptionException ex) {
            throw ex;
        } catch (Exception e) {
            throw new EncryptionException("decrypt failed", e);
        }
    }

    /** Generate a fresh random 32-byte key. Meant for tests + key-rotation tooling. */
    public static byte[] generateKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }
}

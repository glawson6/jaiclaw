package io.jaiclaw.channel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Ed25519 request-signature verification, as used by Discord Interactions.
 *
 * <p>Discord signs each interaction with Ed25519 rather than HMAC, so
 * {@link WebhookSignatureUtil} does not cover it: HMAC needs a shared secret,
 * while this verifies against the application's <em>public</em> key. A forged
 * request cannot be produced without Discord's private key, which is the point
 * — the public key is published in the developer portal and is not a secret.
 *
 * <p>No third-party dependency. The JDK has shipped Ed25519 since Java 15
 * (JEP 339) via the {@code SunEC} provider. Discord publishes the key as 32
 * raw hex-encoded bytes, which is not a format {@code KeyFactory} accepts
 * directly, so {@link #parsePublicKey(String)} wraps it in the fixed 12-byte
 * SubjectPublicKeyInfo prefix for Ed25519 before decoding.
 *
 * <p>The signed payload is {@code timestamp + rawBody} — concatenated, not
 * hashed, and the body must be the <strong>exact bytes received</strong>. Any
 * re-serialisation (parse-then-print) changes whitespace or key order and
 * invalidates the signature.
 */
public final class Ed25519SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(Ed25519SignatureVerifier.class);

    /**
     * DER prefix for an Ed25519 SubjectPublicKeyInfo: SEQUENCE(42) {
     * SEQUENCE(5) { OID 1.3.101.112 }, BIT STRING(33) }. Constant for the
     * algorithm, so the only variable part is the 32-byte key that follows.
     */
    private static final byte[] SPKI_PREFIX =
            HexFormat.of().parseHex("302a300506032b6570032100");

    private static final int RAW_KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 64;

    private Ed25519SignatureVerifier() {
    }

    /**
     * Verifies a Discord-style Ed25519 request signature.
     *
     * <p>Fails closed on every error path — a malformed key, an unparseable
     * signature, or an absent header all return false rather than throwing. A
     * verification helper that throws tends to end up wrapped in a
     * {@code catch} that proceeds anyway.
     *
     * @param publicKeyHex     application public key, 64 hex characters
     * @param signatureHex     value of {@code X-Signature-Ed25519}
     * @param timestamp        value of {@code X-Signature-Timestamp}
     * @param rawBody          the exact request body as received
     * @return true only when the signature is valid for this key and payload
     */
    public static boolean verify(String publicKeyHex, String signatureHex,
                                 String timestamp, String rawBody) {
        if (isBlank(publicKeyHex) || isBlank(signatureHex)
                || isBlank(timestamp) || rawBody == null) {
            return false;
        }
        PublicKey key = parsePublicKey(publicKeyHex);
        if (key == null) {
            return false;
        }
        byte[] signature = decodeHex(signatureHex, "signature");
        if (signature == null || signature.length != SIGNATURE_BYTES) {
            if (signature != null) {
                log.warn("Ed25519 signature is {} bytes, expected {}",
                        signature.length, SIGNATURE_BYTES);
            }
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            // timestamp || body, concatenated. Not hashed, not delimited.
            verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));
            verifier.update(rawBody.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception e) {
            // Includes SignatureException for a structurally invalid signature,
            // which is an expected outcome for a forged request rather than a bug.
            log.debug("Ed25519 verification failed", e);
            return false;
        }
    }

    /**
     * Rebuilds a {@link PublicKey} from 32 raw hex-encoded bytes.
     *
     * @return the key, or null when the input is not a valid Ed25519 key
     */
    public static PublicKey parsePublicKey(String publicKeyHex) {
        byte[] raw = decodeHex(publicKeyHex, "public key");
        if (raw == null) {
            return null;
        }
        if (raw.length != RAW_KEY_BYTES) {
            log.warn("Ed25519 public key is {} bytes, expected {} — check "
                    + "the value is the application public key, not the bot token",
                    raw.length, RAW_KEY_BYTES);
            return null;
        }
        byte[] spki = new byte[SPKI_PREFIX.length + RAW_KEY_BYTES];
        System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
        System.arraycopy(raw, 0, spki, SPKI_PREFIX.length, RAW_KEY_BYTES);
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            log.warn("Could not decode Ed25519 public key", e);
            return null;
        }
    }

    private static byte[] decodeHex(String hex, String what) {
        try {
            return HexFormat.of().parseHex(hex.strip());
        } catch (IllegalArgumentException e) {
            log.warn("Ed25519 {} is not valid hex", what);
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

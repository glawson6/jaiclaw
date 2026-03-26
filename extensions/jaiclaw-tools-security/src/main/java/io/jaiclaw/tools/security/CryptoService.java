package io.jaiclaw.tools.security;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Cryptographic operations for the security handshake protocol.
 * Uses JDK crypto — no external native dependencies.
 */
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64URL_DECODER = Base64.getUrlDecoder();

    /**
     * Generate an EC P-256 key pair for ECDH key exchange.
     */
    public KeyPair generateP256KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), SECURE_RANDOM);
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to generate P-256 key pair", e);
        }
    }

    /**
     * Generate an X25519 key pair for XDH key exchange.
     */
    public KeyPair generateX25519KeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to generate X25519 key pair", e);
        }
    }

    /**
     * Perform ECDH key agreement to derive a shared secret.
     *
     * @param localPrivateKey our private key
     * @param remotePublicKey the peer's public key
     * @param algorithm       "ECDH" for P-256, "XDH" for X25519
     * @return the raw shared secret bytes
     */
    public byte[] keyAgreement(PrivateKey localPrivateKey, PublicKey remotePublicKey, String algorithm) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance(algorithm);
            ka.init(localPrivateKey);
            ka.doPhase(remotePublicKey, true);
            return ka.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Key agreement failed", e);
        }
    }

    /**
     * Encode a public key as Base64url (X.509 DER encoding).
     */
    public String encodePublicKey(PublicKey key) {
        return BASE64URL.encodeToString(key.getEncoded());
    }

    /**
     * Decode a Base64url-encoded public key.
     *
     * @param encoded   Base64url string
     * @param algorithm "EC" for P-256, "X25519" for XDH
     */
    public PublicKey decodePublicKey(String encoded, String algorithm) {
        try {
            byte[] bytes = BASE64URL_DECODER.decode(encoded);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
            KeyFactory kf = KeyFactory.getInstance(algorithm);
            return kf.generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to decode public key", e);
        }
    }

    /**
     * Compute HMAC-SHA256 over the given data using the provided key bytes.
     */
    public byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("HMAC-SHA256 failed", e);
        }
    }

    /**
     * Sign data with HMAC-SHA256 and return Base64url-encoded signature.
     */
    public String hmacSign(byte[] key, String data) {
        byte[] sig = hmacSha256(key, data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return BASE64URL.encodeToString(sig);
    }

    /**
     * Verify an HMAC-SHA256 signature (constant-time comparison).
     */
    public boolean hmacVerify(byte[] key, String data, String signature) {
        String expected = hmacSign(key, data);
        return MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signature.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /**
     * Compute SHA-256 fingerprint of raw bytes, returned as hex string.
     */
    public String sha256Fingerprint(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("SHA-256 not available", e);
        }
    }

    /**
     * Generate a cryptographically random nonce (32 bytes), Base64url-encoded.
     */
    public String generateNonce() {
        byte[] nonce = new byte[32];
        SECURE_RANDOM.nextBytes(nonce);
        return BASE64URL.encodeToString(nonce);
    }

    /**
     * Create a signed JWT session token using the given shared secret as HMAC key.
     *
     * @param sharedSecret raw shared secret bytes (used as HMAC-SHA256 signing key)
     * @param subject      the identity subject (e.g. clientId or handshakeId)
     * @param claims       additional claims to embed
     * @param ttlSeconds   token time-to-live
     * @return signed JWT string
     */
    public String createSessionToken(byte[] sharedSecret, String subject,
                                     Map<String, Object> claims, int ttlSeconds) {
        SecretKey signingKey = new SecretKeySpec(sharedSecret, "HmacSHA256");
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey, Jwts.SIG.HS256);
        claims.forEach(builder::claim);
        return builder.compact();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Unchecked exception for crypto operation failures.
     */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

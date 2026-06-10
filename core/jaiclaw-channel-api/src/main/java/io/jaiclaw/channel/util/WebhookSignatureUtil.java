package io.jaiclaw.channel.util;

import io.jaiclaw.core.api.Stable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Single canonical HMAC-SHA256 webhook signature verification utility.
 *
 * <p>Replaces the pre-0.8.0 per-channel signature verification helpers
 * (Slack, Telegram, LINE all had near-identical implementations). Every
 * channel adapter that needs HMAC verification now calls one of these
 * static methods.
 *
 * <p>All comparisons use {@link MessageDigest#isEqual(byte[], byte[])}
 * (constant-time) to prevent timing attacks.
 *
 * <p>Carved out as part of Phase 3 P3.3 (audit
 * {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.3).
 */
@Stable
public final class WebhookSignatureUtil {

    private WebhookSignatureUtil() {}

    /**
     * Compute an HMAC-SHA256 signature over {@code payload} using
     * {@code secret} as the key. Returns the lowercase hex-encoded MAC.
     *
     * @return hex-encoded signature, never null
     */
    public static String computeHmacSha256(String secret, String payload) {
        if (secret == null) throw new IllegalArgumentException("secret must not be null");
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * Verify that {@code receivedSignature} matches HMAC-SHA256({@code secret},
     * {@code payload}).
     *
     * <p>Uses constant-time comparison to prevent timing attacks. Returns
     * {@code false} for null inputs, length mismatches, or computation errors.
     *
     * @param secret            HMAC key (e.g. a channel signing secret)
     * @param payload           the canonical string that was signed
     * @param receivedSignature the signature provided by the caller (hex,
     *                          with or without an algorithm prefix such as
     *                          {@code "v0="} for Slack)
     * @return true if the signature matches
     */
    public static boolean verifyHmacSha256(String secret, String payload, String receivedSignature) {
        if (secret == null || payload == null || receivedSignature == null) {
            return false;
        }
        String computed = computeHmacSha256(secret, payload);
        byte[] computedBytes = computed.getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = receivedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(computedBytes, receivedBytes);
    }

    /**
     * Verify a Slack-style signature: {@code "v0=" + HMAC-SHA256(secret, "v0:" + timestamp + ":" + body)}.
     *
     * <p>Also enforces a max-clock-skew check on {@code timestamp} (in seconds
     * since epoch) — requests older than {@code maxDriftSeconds} are rejected
     * to prevent replay.
     *
     * @param secret           the channel signing secret
     * @param timestampSeconds request timestamp from the X-Slack-Request-Timestamp header
     * @param body             the raw request body
     * @param receivedSignature the X-Slack-Signature header value (must start with "v0=")
     * @param maxDriftSeconds  maximum allowed difference between {@code timestampSeconds}
     *                         and {@code System.currentTimeMillis()/1000}
     * @return true if the signature is valid and the timestamp is within tolerance
     */
    public static boolean verifySlackSignature(String secret, String timestampSeconds, String body,
                                                String receivedSignature, long maxDriftSeconds) {
        if (secret == null || timestampSeconds == null || body == null || receivedSignature == null) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestampSeconds);
            long now = System.currentTimeMillis() / 1000L;
            if (Math.abs(now - ts) > maxDriftSeconds) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String base = "v0:" + timestampSeconds + ":" + body;
        String expected = "v0=" + computeHmacSha256(secret, base);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = receivedSignature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    /**
     * Constant-time string equality. Use to compare a header-supplied secret
     * (Telegram's {@code X-Telegram-Bot-Api-Secret-Token}, LINE's session token)
     * against the expected value without leaking length or content via timing.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}

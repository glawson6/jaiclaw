package io.jaiclaw.identity.link;

import java.time.Instant;

/**
 * A pending channel-link request, held between issuing the authorization URL
 * and the provider redirecting back.
 *
 * <p>Carries the PKCE verifier, so it is <strong>secret</strong>: anyone holding
 * it could complete the exchange for the code it pairs with. Never log it, and
 * never return it over an API.
 *
 * @param nonce         opaque single-use identifier, travels as the OAuth {@code state}
 * @param channel       the channel being linked ("telegram", "slack", …)
 * @param channelUserId the platform-specific user id being linked
 * @param tenantId      tenant the request was issued under, or null in single-tenant
 * @param pkceVerifier  PKCE code verifier — secret
 * @param issuedAt      when the request was created
 * @param expiresAt     when it stops being redeemable
 *
 * <p>1.4.0.
 */
public record ChannelLinkRequest(
        String nonce,
        String channel,
        String channelUserId,
        String tenantId,
        String pkceVerifier,
        Instant issuedAt,
        Instant expiresAt
) {
    /** Whether this request is past its expiry as of {@code now}. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Redacts the PKCE verifier — records like this end up in logs by accident. */
    @Override
    public String toString() {
        return "ChannelLinkRequest[nonce=" + nonce + ", channel=" + channel
                + ", channelUserId=" + channelUserId + ", tenantId=" + tenantId
                + ", pkceVerifier=<redacted>, expiresAt=" + expiresAt + "]";
    }
}

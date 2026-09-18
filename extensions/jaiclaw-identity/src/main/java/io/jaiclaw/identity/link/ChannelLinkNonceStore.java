package io.jaiclaw.identity.link;

import java.util.Optional;

/**
 * Holds pending {@link ChannelLinkRequest}s between issue and redemption.
 *
 * <p>The reference implementation {@link InMemoryChannelLinkNonceStore} lives in
 * this module; adopters running more than one gateway pod must supply a shared
 * implementation (Redis, JDBC) as a {@code @Bean ChannelLinkNonceStore},
 * because the callback may land on a different pod than issued the nonce.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><strong>Redemption is single-use.</strong> {@link #redeem} must
 *       atomically return and remove. A nonce that can be redeemed twice is a
 *       replay vector — an attacker who observes one callback URL could re-link
 *       the same channel user.</li>
 *   <li><strong>Expired entries must not be returned</strong>, whether or not
 *       they have been swept.</li>
 *   <li>Implementations must scope storage to the current tenant in
 *       multi-tenant mode.</li>
 * </ul>
 *
 * <p>1.4.0.
 */
public interface ChannelLinkNonceStore {

    /** Store a pending request. */
    void store(ChannelLinkRequest request);

    /**
     * Atomically return and remove the request for {@code nonce}.
     *
     * @return the request, or empty when unknown, already redeemed, or expired
     */
    Optional<ChannelLinkRequest> redeem(String nonce);

    /** Drop expired entries. Implementations may also expire lazily. */
    default void purgeExpired() {
        // no-op by default — stores with native TTL have nothing to do
    }

    /** Pending request count, for diagnostics. {@code -1} when not tracked. */
    default int size() {
        return -1;
    }
}

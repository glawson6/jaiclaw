package io.jaiclaw.identity.link;

import io.jaiclaw.core.model.IdentityLink;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.identity.IdentityLinkStore;
import io.jaiclaw.identity.oauth.PkceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Proves that the person on a channel controls an external identity, and
 * records the result as a verified {@link IdentityLink}.
 *
 * <h2>Why this exists</h2>
 * Channel identity is otherwise <em>asserted, never verified</em>. A Slack user
 * id arrives in an inbound message and is taken at face value; nothing binds it
 * to a real person, and on some channels it is not even a user id (Slack and
 * Discord report a channel). That makes per-user authorization impossible and
 * leaves GDPR erasure operating on a per-channel pseudonym.
 *
 * <p>The ceremony is a standard OAuth authorization-code flow with PKCE:
 *
 * <pre>
 * 1. issue(channel, peerId) → a nonce and an authorization URL
 * 2. the agent sends the URL to the user on the channel
 * 3. the user authenticates with the identity provider
 * 4. the provider redirects to the gateway callback with code + state
 * 5. complete(state, code) → exchanges the code, records a verified link
 * </pre>
 *
 * <p>Nothing here is provider-specific. Endpoints and client credentials are
 * configuration; the flow is RFC 6749 + RFC 7636.
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li><strong>PKCE is mandatory</strong>, not conditional on a client secret.
 *       The redirect passes through the user's browser, so an intercepted code
 *       is useless without the verifier.</li>
 *   <li><strong>The nonce is the OAuth {@code state}</strong> and is single-use,
 *       which makes it CSRF protection and replay protection at once.</li>
 *   <li><strong>The channel binding is server-side.</strong> Which channel user
 *       is being linked comes from the stored request, never from the callback —
 *       otherwise anyone could complete a flow and bind it to someone else's
 *       channel id.</li>
 * </ul>
 *
 * <p>1.4.0.
 */
public class ChannelLinkService {

    private static final Logger log = LoggerFactory.getLogger(ChannelLinkService.class);

    private final ChannelLinkProperties properties;
    private final ChannelLinkNonceStore nonceStore;
    private final IdentityLinkStore linkStore;
    private final TokenExchanger tokenExchanger;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public ChannelLinkService(ChannelLinkProperties properties,
                             ChannelLinkNonceStore nonceStore,
                             IdentityLinkStore linkStore,
                             TokenExchanger tokenExchanger,
                             TenantGuard tenantGuard,
                             Clock clock) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        this.linkStore = linkStore;
        this.tokenExchanger = tokenExchanger;
        this.tenantGuard = tenantGuard != null
                ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Begin linking: mint a nonce and build the authorization URL to send the user.
     *
     * @param channel       channel being linked, e.g. "telegram"
     * @param channelUserId the platform user id
     * @return the nonce and the URL the user should open
     * @throws IllegalStateException when linking is not fully configured
     */
    public LinkInvitation issue(String channel, String channelUserId) {
        if (!properties.isUsable()) {
            throw new IllegalStateException(
                    "Channel linking is not configured. Set jaiclaw.identity.link.enabled=true "
                            + "along with authorize-uri, token-uri, client-id and redirect-uri.");
        }
        PkceGenerator.PkceChallenge pkce = PkceGenerator.generate();
        String nonce = PkceGenerator.generateState();
        Instant now = clock.instant();

        ChannelLinkRequest request = new ChannelLinkRequest(
                nonce, channel, channelUserId,
                tenantGuard.requireTenantIfMulti(),
                pkce.verifier(),
                now, now.plus(properties.nonceTtl()));
        nonceStore.store(request);

        log.debug("Issued link nonce for {}:{} (expires {})",
                channel, channelUserId, request.expiresAt());
        return new LinkInvitation(nonce, buildAuthorizationUrl(nonce, pkce.challenge()),
                request.expiresAt());
    }

    /**
     * Complete linking from the provider's callback.
     *
     * @param state the {@code state} the provider echoed back — our nonce
     * @param code  the authorization code
     * @return the verified link, or empty when the state is unknown, already
     *         used, or expired
     */
    public Optional<IdentityLink> complete(String state, String code) {
        Optional<ChannelLinkRequest> maybeRequest = nonceStore.redeem(state);
        if (maybeRequest.isEmpty()) {
            // Covers unknown, replayed, and expired alike. Deliberately not
            // distinguished: telling a caller which one it was helps an attacker
            // probing for live nonces and helps a legitimate user not at all.
            log.warn("Channel link callback with unknown, expired, or already-used state");
            return Optional.empty();
        }
        ChannelLinkRequest request = maybeRequest.get();

        TokenExchanger.ExchangeResult result;
        try {
            result = tokenExchanger.exchange(code, request.pkceVerifier());
        } catch (RuntimeException e) {
            log.warn("Token exchange failed while linking {}:{}: {}",
                    request.channel(), request.channelUserId(), e.getMessage());
            return Optional.empty();
        }

        if (result == null || result.subject() == null || result.subject().isBlank()) {
            log.warn("Identity provider returned no subject while linking {}:{}",
                    request.channel(), request.channelUserId());
            return Optional.empty();
        }

        // The channel binding comes from the STORED request, never the callback.
        // Taking it from the callback would let anyone complete a flow of their
        // own and bind it to someone else's channel id.
        IdentityLink link = IdentityLink.verified(
                result.subject(),
                request.channel(),
                request.channelUserId(),
                result.tenantId() != null ? result.tenantId() : request.tenantId(),
                result.issuer(),
                clock.instant());

        linkStore.link(link.canonicalUserId(), link.channel(), link.channelUserId(),
                link.issuer(), link.verifiedAt());

        log.info("Verified identity link: {}:{} → subject {} (issuer {})",
                link.channel(), link.channelUserId(), link.canonicalUserId(), link.issuer());
        return Optional.of(link);
    }

    private String buildAuthorizationUrl(String nonce, String challenge) {
        StringBuilder url = new StringBuilder(properties.authorizeUri());
        url.append(properties.authorizeUri().contains("?") ? '&' : '?');
        url.append("response_type=code");
        append(url, "client_id", properties.clientId());
        append(url, "redirect_uri", properties.redirectUri());
        append(url, "scope", String.join(" ", properties.scopes()));
        append(url, "state", nonce);
        append(url, "code_challenge", challenge);
        append(url, "code_challenge_method", "S256");
        if (properties.resource() != null) {
            // RFC 8707. Some providers only mint a correctly-audienced token when
            // the request names the resource explicitly.
            append(url, "resource", properties.resource());
        }
        return url.toString();
    }

    private static void append(StringBuilder url, String key, String value) {
        url.append('&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    /**
     * What to send the user.
     *
     * @param nonce         the OAuth state, also the key to redeem the request
     * @param authorizeUrl  the URL the user should open
     * @param expiresAt     when the invitation stops working
     */
    public record LinkInvitation(String nonce, String authorizeUrl, Instant expiresAt) {}

    /**
     * Exchanges an authorization code for an identity.
     *
     * <p>A separate interface so the HTTP client is pluggable and, more
     * usefully, so the ceremony is testable without a live provider.
     */
    public interface TokenExchanger {

        /**
         * @param code         the authorization code
         * @param pkceVerifier the verifier paired with the challenge already sent
         * @return the caller's identity, never null
         */
        ExchangeResult exchange(String code, String pkceVerifier);

        /**
         * @param subject  the provider's subject claim — the canonical user id
         * @param issuer   the provider's issuer, recorded as link provenance
         * @param tenantId organization claim when the provider supplied one
         */
        record ExchangeResult(String subject, String issuer, String tenantId) {}
    }
}

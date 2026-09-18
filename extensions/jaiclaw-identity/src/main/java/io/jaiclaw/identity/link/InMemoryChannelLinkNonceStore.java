package io.jaiclaw.identity.link;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ChannelLinkNonceStore}. The default.
 *
 * <p>Suitable for single-pod deployments. <strong>Not</strong> suitable for more
 * than one gateway replica: the callback may land on a pod that never saw the
 * nonce, and the link silently fails. Multi-pod deployments must supply a shared
 * implementation.
 *
 * <p>Redemption removes atomically via {@code ConcurrentHashMap.remove}, so a
 * nonce cannot be redeemed twice even under concurrent callbacks.
 *
 * <p>1.4.0.
 */
public class InMemoryChannelLinkNonceStore implements ChannelLinkNonceStore {

    private static final Logger log =
            LoggerFactory.getLogger(InMemoryChannelLinkNonceStore.class);

    private final Map<String, ChannelLinkRequest> pending = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public InMemoryChannelLinkNonceStore() {
        this(new TenantGuard(TenantProperties.DEFAULT), Clock.systemUTC());
    }

    public InMemoryChannelLinkNonceStore(TenantGuard tenantGuard, Clock clock) {
        this.tenantGuard = tenantGuard != null
                ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public void store(ChannelLinkRequest request) {
        pending.put(key(request.nonce()), request);
    }

    @Override
    public Optional<ChannelLinkRequest> redeem(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return Optional.empty();
        }
        // remove() is the atomic step — a second concurrent redemption gets null.
        ChannelLinkRequest request = pending.remove(key(nonce));
        if (request == null) {
            return Optional.empty();
        }
        if (request.isExpired(clock.instant())) {
            log.debug("Link nonce expired for {}:{}", request.channel(), request.channelUserId());
            return Optional.empty();
        }
        return Optional.of(request);
    }

    @Override
    public void purgeExpired() {
        Instant now = clock.instant();
        pending.values().removeIf(r -> r.isExpired(now));
    }

    @Override
    public int size() {
        return pending.size();
    }

    /**
     * Physically prefixes the tenant so a nonce issued under one tenant cannot
     * be redeemed under another, even though nonces are random.
     */
    private String key(String nonce) {
        String prefix = tenantGuard.resolveTenantPrefix();
        return prefix.isEmpty() ? nonce : prefix + ":" + nonce;
    }
}

package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Experimental;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches another {@link SecretsProvider}'s answers, including misses.
 *
 * <p>Wraps any provider, but exists for the 1Password one:
 * {@code OnePasswordSecretsProvider} forks an {@code op read} subprocess per
 * lookup, and {@code SecretsPropertySource} is {@code addFirst}-ed into the
 * Spring {@code Environment} — so it is consulted before every other property
 * source, for <em>every</em> property the application resolves. Each miss
 * therefore paid a process spawn, a network round-trip to 1Password, and up to
 * the provider's timeout budget.
 *
 * <h2>Negative caching is the point</h2>
 *
 * <p>Misses are both the expensive case and by far the common one — the vast
 * majority of property lookups are not secrets at all. A cache that stored only
 * hits would leave the hot path exactly as slow as before. Misses are therefore
 * cached too, under a separate and normally shorter TTL, because a secret that
 * does not exist yet is more likely to appear than a resolved one is to change.
 *
 * <h2>Why not Caffeine</h2>
 *
 * <p>{@code jaiclaw-core} deliberately has no third-party dependencies, and this
 * needs neither eviction policy nor weighted sizing — a bounded map with a
 * timestamp per entry covers it. Caffeine is used elsewhere in the reactor where
 * a real cache is warranted.
 *
 * <h2>Secret material in memory</h2>
 *
 * <p>Caching a secret keeps it in the heap for the TTL. That is already true of
 * every resolved {@code @Value} and of the provider's own return value, so the
 * cache does not change the exposure class — but it does extend the window, and
 * {@link #refresh()} clears everything so a rotation can be made to take effect
 * without a restart.
 */
@Experimental
public final class CachingSecretsProvider implements SecretsProvider {

    /** Long enough to collapse a startup storm, short enough that rotation lands. */
    public static final Duration DEFAULT_HIT_TTL = Duration.ofMinutes(5);

    /** Shorter: an absent secret is likelier to appear than a present one is to change. */
    public static final Duration DEFAULT_MISS_TTL = Duration.ofMinutes(1);

    /** Bounded so a pathological lookup pattern cannot grow the heap without limit. */
    public static final int DEFAULT_MAX_ENTRIES = 4_096;

    private record Entry(String value, long expiresAtMillis) {
        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }

        /** A miss is a present entry with a null value, so it can expire separately. */
        boolean isMiss() {
            return value == null;
        }
    }

    private final SecretsProvider delegate;
    private final Duration hitTtl;
    private final Duration missTtl;
    private final int maxEntries;
    private final Clock clock;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong delegateCalls = new AtomicLong();

    public CachingSecretsProvider(SecretsProvider delegate) {
        this(delegate, DEFAULT_HIT_TTL, DEFAULT_MISS_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    public CachingSecretsProvider(SecretsProvider delegate, Duration hitTtl, Duration missTtl) {
        this(delegate, hitTtl, missTtl, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    public CachingSecretsProvider(SecretsProvider delegate, Duration hitTtl, Duration missTtl,
                                  int maxEntries, Clock clock) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.hitTtl = positiveOr(hitTtl, DEFAULT_HIT_TTL);
        this.missTtl = positiveOr(missTtl, DEFAULT_MISS_TTL);
        this.maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        long now = clock.millis();
        Entry cached = cache.get(key);
        if (cached != null && !cached.isExpired(now)) {
            hits.incrementAndGet();
            return cached.isMiss() ? Optional.empty() : Optional.of(cached.value());
        }

        misses.incrementAndGet();
        delegateCalls.incrementAndGet();
        Optional<String> resolved;
        try {
            resolved = delegate.get(key);
        } catch (RuntimeException e) {
            // Do not cache failures. A provider error is usually transient (the
            // CLI was missing, the network blipped); caching it would turn a
            // momentary outage into a TTL-long one.
            cache.remove(key);
            throw e;
        }

        evictIfFull();
        Duration ttl = resolved.isPresent() ? hitTtl : missTtl;
        cache.put(key, new Entry(resolved.orElse(null), now + ttl.toMillis()));
        return resolved;
    }

    /**
     * Not cached — delegated straight through.
     *
     * <p>Prefix queries are rare, their result sets are unbounded, and the key
     * for a prefix lookup is not the key for the per-secret lookups it implies,
     * so caching it would risk serving a stale set while individual keys were
     * fresh. The hot path this class exists for is {@link #get(String)}.
     */
    @Override
    public Map<String, String> getAll(String prefix) {
        return delegate.getAll(prefix);
    }

    @Override
    public String name() {
        return "caching(" + delegate.name() + ")";
    }

    /**
     * Clears the cache and refreshes the delegate.
     *
     * <p>This is the rotation hook: after rotating a secret in the backing store,
     * {@code refresh()} makes the new value visible without a restart. The SPI
     * has always declared this method; nothing implemented it, because nothing
     * cached.
     */
    @Override
    public void refresh() {
        // No logging here: jaiclaw-core carries no logging dependency by design.
        // Callers that want visibility can read size() before and after.
        cache.clear();
        delegate.refresh();
    }

    /**
     * Drops the oldest-expiring entries when at capacity.
     *
     * <p>Expired entries first; only if none are expired does it evict a live
     * one. Not an LRU — this bound exists to stop unbounded growth from a
     * pathological lookup pattern, not to maximise hit rate.
     */
    private void evictIfFull() {
        if (cache.size() < maxEntries) {
            return;
        }
        long now = clock.millis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(now));
        if (cache.size() >= maxEntries) {
            cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue(
                            (a, b) -> Long.compare(a.expiresAtMillis(), b.expiresAtMillis())))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
    }

    private static Duration positiveOr(Duration candidate, Duration fallback) {
        return (candidate == null || candidate.isZero() || candidate.isNegative())
                ? fallback : candidate;
    }

    // --- diagnostics ---

    /** Lookups served from cache. */
    public long cacheHits() {
        return hits.get();
    }

    /** Lookups that had to consult the delegate. */
    public long cacheMisses() {
        return misses.get();
    }

    /**
     * Calls that reached the delegate — for 1Password, the number of {@code op}
     * subprocesses spawned. The figure this class exists to keep small.
     */
    public long delegateCalls() {
        return delegateCalls.get();
    }

    /** Current entry count, expired-but-unevicted included. */
    public int size() {
        return cache.size();
    }
}

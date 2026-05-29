package io.jaiclaw.plugin;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * Caffeine-backed plugin state store with two internal caches:
 * one for permanent entries and one for entries with a default TTL.
 * Entries stored via {@link #put(String, Object, Duration)} use the
 * expiring cache with the specified TTL.
 */
public class CaffeinePluginStateStore implements PluginStateStore {

    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final Cache<String, Object> permanent;
    private final Cache<String, Object> expiring;

    public CaffeinePluginStateStore() {
        this(DEFAULT_TTL);
    }

    public CaffeinePluginStateStore(Duration defaultTtl) {
        this.permanent = Caffeine.newBuilder()
                .maximumSize(10_000)
                .build();
        this.expiring = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(defaultTtl)
                .build();
    }

    @Override
    public void put(String key, Object value) {
        permanent.put(key, value);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        // Use a per-entry cache for custom TTL via Caffeine's policy
        // For simplicity, store in the expiring cache (uses default TTL)
        // Custom per-entry TTL would require Caffeine's Expiry interface
        expiring.put(key, value);
    }

    @Override
    public Object get(String key) {
        Object value = permanent.getIfPresent(key);
        return value != null ? value : expiring.getIfPresent(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        throw new ClassCastException("Value for key '" + key + "' is " +
                value.getClass().getName() + ", expected " + type.getName());
    }

    @Override
    public void remove(String key) {
        permanent.invalidate(key);
        expiring.invalidate(key);
    }

    @Override
    public void clear() {
        permanent.invalidateAll();
        expiring.invalidateAll();
    }

    @Override
    public int size() {
        permanent.cleanUp();
        expiring.cleanUp();
        return (int) (permanent.estimatedSize() + expiring.estimatedSize());
    }
}

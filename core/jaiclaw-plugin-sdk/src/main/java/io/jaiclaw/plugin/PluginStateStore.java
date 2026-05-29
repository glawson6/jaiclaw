package io.jaiclaw.plugin;

import java.time.Duration;

/**
 * Per-plugin ephemeral key-value storage with optional TTL support.
 * Each plugin gets its own isolated store via {@link PluginApi#stateStore()}.
 */
public interface PluginStateStore {

    void put(String key, Object value);

    void put(String key, Object value, Duration ttl);

    Object get(String key);

    <T> T get(String key, Class<T> type);

    void remove(String key);

    void clear();

    int size();
}

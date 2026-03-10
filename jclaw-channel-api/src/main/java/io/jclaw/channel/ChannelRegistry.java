package io.jclaw.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available channel adapters. The gateway uses this to route
 * outbound messages and to start/stop all adapters during lifecycle events.
 */
public class ChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegistry.class);

    private final Map<String, ChannelAdapter> adapters = new ConcurrentHashMap<>();

    public void register(ChannelAdapter adapter) {
        var previous = adapters.putIfAbsent(adapter.channelId(), adapter);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate channel adapter for: " + adapter.channelId());
        }
        log.info("Registered channel adapter: {} ({})", adapter.channelId(), adapter.displayName());
    }

    public Optional<ChannelAdapter> get(String channelId) {
        return Optional.ofNullable(adapters.get(channelId));
    }

    public Collection<ChannelAdapter> all() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    public Set<String> channelIds() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    public int size() {
        return adapters.size();
    }

    public boolean contains(String channelId) {
        return adapters.containsKey(channelId);
    }

    public void unregister(String channelId) {
        var adapter = adapters.remove(channelId);
        if (adapter != null && adapter.isRunning()) {
            adapter.stop();
        }
    }

    /**
     * Start all registered adapters with the given message handler.
     */
    public void startAll(ChannelMessageHandler handler) {
        adapters.values().forEach(adapter -> {
            try {
                adapter.start(handler);
                log.info("Started channel adapter: {}", adapter.channelId());
            } catch (Exception e) {
                log.error("Failed to start channel adapter: {}", adapter.channelId(), e);
            }
        });
    }

    /**
     * Stop all running adapters.
     */
    public void stopAll() {
        adapters.values().forEach(adapter -> {
            try {
                if (adapter.isRunning()) {
                    adapter.stop();
                    log.info("Stopped channel adapter: {}", adapter.channelId());
                }
            } catch (Exception e) {
                log.error("Failed to stop channel adapter: {}", adapter.channelId(), e);
            }
        });
    }
}

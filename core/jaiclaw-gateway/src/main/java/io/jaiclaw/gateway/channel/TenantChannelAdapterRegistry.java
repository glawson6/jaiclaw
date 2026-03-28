package io.jaiclaw.gateway.channel;

import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.config.TenantChannelsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for per-tenant channel adapters. Each tenant can have its own
 * set of channel adapters (e.g., its own Telegram bot, Slack workspace).
 *
 * <p>In SINGLE mode, this registry is empty and all messages go through
 * the global {@link io.jaiclaw.channel.ChannelRegistry} singleton adapters.
 */
public class TenantChannelAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantChannelAdapterRegistry.class);

    // Map<tenantId, Map<channelId, ChannelAdapter>>
    private final ConcurrentHashMap<String, Map<String, ChannelAdapter>> tenantAdapters = new ConcurrentHashMap<>();

    /**
     * Register a channel adapter for a specific tenant.
     */
    public void registerAdapter(String tenantId, String channelId, ChannelAdapter adapter) {
        tenantAdapters.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                .put(channelId, adapter);
        log.debug("Registered {} adapter for tenant: {}", channelId, tenantId);
    }

    /**
     * Get the channel adapter for a specific tenant + channel.
     */
    public Optional<ChannelAdapter> getAdapter(String tenantId, String channelId) {
        Map<String, ChannelAdapter> adapters = tenantAdapters.get(tenantId);
        if (adapters == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(channelId));
    }

    /**
     * Start all registered adapters for a tenant.
     */
    public void startTenant(String tenantId, ChannelMessageHandler handler) {
        Map<String, ChannelAdapter> adapters = tenantAdapters.get(tenantId);
        if (adapters == null) return;
        adapters.values().forEach(adapter -> {
            try {
                adapter.start(handler);
                log.info("Started {} adapter for tenant: {}", adapter.channelId(), tenantId);
            } catch (Exception e) {
                log.error("Failed to start {} adapter for tenant {}: {}",
                        adapter.channelId(), tenantId, e.getMessage());
            }
        });
    }

    /**
     * Stop and remove all adapters for a tenant.
     */
    public void unregisterTenant(String tenantId) {
        Map<String, ChannelAdapter> adapters = tenantAdapters.remove(tenantId);
        if (adapters != null) {
            adapters.values().forEach(adapter -> {
                try {
                    adapter.stop();
                } catch (Exception e) {
                    log.warn("Error stopping adapter {}: {}", adapter.channelId(), e.getMessage());
                }
            });
            log.info("Unregistered all adapters for tenant: {}", tenantId);
        }
    }

    /**
     * Check if any adapters are registered for a tenant.
     */
    public boolean hasTenant(String tenantId) {
        return tenantAdapters.containsKey(tenantId);
    }

    /**
     * Total number of registered tenants.
     */
    public int tenantCount() {
        return tenantAdapters.size();
    }
}

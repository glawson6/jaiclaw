package io.jclaw.gateway.observability;

import io.jclaw.channel.ChannelRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator for the gateway — reports on channel adapter status.
 * Compatible with Spring Boot Actuator HealthIndicator pattern.
 */
public class GatewayHealthIndicator {

    private final ChannelRegistry channelRegistry;
    private final GatewayMetrics metrics;

    public GatewayHealthIndicator(ChannelRegistry channelRegistry, GatewayMetrics metrics) {
        this.channelRegistry = channelRegistry;
        this.metrics = metrics;
    }

    public Map<String, Object> health() {
        var details = new LinkedHashMap<String, Object>();
        boolean allUp = true;

        var channels = channelRegistry.all();
        for (var adapter : channels) {
            boolean running = adapter.isRunning();
            details.put("channel." + adapter.channelId(), running ? "UP" : "DOWN");
            if (!running) allUp = false;
        }

        details.put("channelCount", channels.size());
        details.put("totalMessages", metrics.totalMessages());
        details.put("totalErrors", metrics.totalErrors());
        details.put("status", channels.isEmpty() ? "UNKNOWN" : (allUp ? "UP" : "DEGRADED"));

        return details;
    }

    public boolean isHealthy() {
        return channelRegistry.all().stream().allMatch(a -> a.isRunning());
    }
}

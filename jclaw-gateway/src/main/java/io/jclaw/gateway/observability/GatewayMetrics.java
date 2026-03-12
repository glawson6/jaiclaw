package io.jclaw.gateway.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple metrics collector for gateway operations.
 * Tracks message counts, latency, and error rates per channel.
 * Can be backed by Micrometer when on classpath.
 */
public class GatewayMetrics {

    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final Map<String, AtomicLong> messagesByChannel = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorsByChannel = new ConcurrentHashMap<>();
    private final AtomicLong totalToolExecutions = new AtomicLong();
    private final AtomicLong totalToolErrors = new AtomicLong();

    public void recordMessage(String channelId) {
        totalMessages.incrementAndGet();
        messagesByChannel.computeIfAbsent(channelId, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordError(String channelId) {
        totalErrors.incrementAndGet();
        errorsByChannel.computeIfAbsent(channelId, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordToolExecution(boolean success) {
        totalToolExecutions.incrementAndGet();
        if (!success) totalToolErrors.incrementAndGet();
    }

    public long totalMessages() { return totalMessages.get(); }
    public long totalErrors() { return totalErrors.get(); }
    public long messagesForChannel(String channelId) {
        var counter = messagesByChannel.get(channelId);
        return counter != null ? counter.get() : 0;
    }
    public long errorsForChannel(String channelId) {
        var counter = errorsByChannel.get(channelId);
        return counter != null ? counter.get() : 0;
    }
    public long totalToolExecutions() { return totalToolExecutions.get(); }
    public long totalToolErrors() { return totalToolErrors.get(); }

    public Map<String, Object> snapshot() {
        var snap = new ConcurrentHashMap<String, Object>();
        snap.put("totalMessages", totalMessages.get());
        snap.put("totalErrors", totalErrors.get());
        snap.put("totalToolExecutions", totalToolExecutions.get());
        snap.put("totalToolErrors", totalToolErrors.get());
        messagesByChannel.forEach((k, v) -> snap.put("messages." + k, v.get()));
        return snap;
    }

    public void reset() {
        totalMessages.set(0);
        totalErrors.set(0);
        messagesByChannel.clear();
        errorsByChannel.clear();
        totalToolExecutions.set(0);
        totalToolErrors.set(0);
    }
}

package io.jclaw.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatches incoming webhook requests to the appropriate channel handler.
 * Channel adapters register their webhook handlers here during startup.
 */
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final Map<String, WebhookHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Register a webhook handler for a channel.
     */
    public void register(String channelId, WebhookHandler handler) {
        handlers.put(channelId, handler);
        log.info("Registered webhook handler for channel: {}", channelId);
    }

    /**
     * Dispatch an incoming webhook to the appropriate handler.
     */
    public ResponseEntity<String> dispatch(String channelId, String body, Map<String, String> headers) {
        var handler = handlers.get(channelId);
        if (handler == null) {
            log.warn("No webhook handler for channel: {}", channelId);
            return ResponseEntity.notFound().build();
        }

        try {
            return handler.handle(body, headers);
        } catch (Exception e) {
            log.error("Webhook handler failed for channel: {}", channelId, e);
            return ResponseEntity.internalServerError().body("Internal error");
        }
    }

    public Set<String> registeredChannels() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Functional interface for handling raw webhook payloads.
     */
    @FunctionalInterface
    public interface WebhookHandler {
        ResponseEntity<String> handle(String body, Map<String, String> headers);
    }
}

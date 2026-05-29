package io.jaiclaw.gateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of webhook routes keyed by path.
 */
public class WebhookRouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebhookRouteRegistry.class);

    private final ConcurrentMap<String, WebhookRoute> routes = new ConcurrentHashMap<>();

    /**
     * Register a webhook route. Overwrites any existing route at the same path.
     */
    public void register(WebhookRoute route) {
        routes.put(normalizePath(route.path()), route);
        log.info("Registered webhook route: {} (auth: {})", route.path(), route.authType());
    }

    /**
     * Unregister a webhook route by path.
     */
    public boolean unregister(String path) {
        return routes.remove(normalizePath(path)) != null;
    }

    /**
     * Look up a route by path.
     */
    public Optional<WebhookRoute> findByPath(String path) {
        return Optional.ofNullable(routes.get(normalizePath(path)));
    }

    /**
     * Get all registered paths.
     */
    public Set<String> registeredPaths() {
        return Collections.unmodifiableSet(routes.keySet());
    }

    /**
     * Number of registered routes.
     */
    public int size() {
        return routes.size();
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}

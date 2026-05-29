package io.jaiclaw.websearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of web search providers with an active provider selection.
 */
public class WebSearchRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSearchRegistry.class);

    private final Map<String, WebSearchProvider> providers = new ConcurrentHashMap<>();
    private volatile String activeProviderId;

    public void register(WebSearchProvider provider) {
        providers.put(provider.id(), provider);
        log.debug("Registered web search provider: {}", provider.id());
    }

    public Optional<WebSearchProvider> activeProvider() {
        if (activeProviderId != null) {
            WebSearchProvider p = providers.get(activeProviderId);
            if (p != null && p.isConfigured()) return Optional.of(p);
        }
        // Fallback: find first configured provider
        return providers.values().stream()
                .filter(WebSearchProvider::isConfigured)
                .findFirst();
    }

    public void setActiveProvider(String id) {
        if (!providers.containsKey(id)) {
            throw new IllegalArgumentException("Unknown provider: " + id + ". Available: " + providers.keySet());
        }
        this.activeProviderId = id;
        log.info("Active web search provider set to: {}", id);
    }

    public Set<String> providerIds() {
        return Set.copyOf(providers.keySet());
    }
}

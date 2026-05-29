package io.jaiclaw.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wraps a delegate {@link MemorySearchManager} with a {@link MemoryCircuitBreaker}
 * and respects {@link MemoryToggleStore} per-session toggles.
 * When the circuit is open or memory is disabled, returns an empty list.
 */
public class CircuitBreakerMemorySearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMemorySearchManager.class);

    private final MemorySearchManager delegate;
    private final MemoryCircuitBreaker circuitBreaker;
    private final MemoryToggleStore toggleStore;

    public CircuitBreakerMemorySearchManager(MemorySearchManager delegate,
                                              MemoryToggleStore toggleStore,
                                              MemoryCircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.toggleStore = toggleStore;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public List<MemorySearchResult> search(String query, MemorySearchOptions options) {
        // Check per-session toggle
        if (options.sessionKey() != null && !toggleStore.isEnabled(options.sessionKey())) {
            log.debug("Memory search disabled for session: {}", options.sessionKey());
            return List.of();
        }

        return circuitBreaker.run(
                () -> delegate.search(query, options),
                ex -> {
                    log.warn("Memory search failed: {}", ex.getMessage());
                    return List.of();
                }
        );
    }
}

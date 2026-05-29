package io.jaiclaw.memory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Wraps a delegate {@link MemorySearchManager} with a Resilience4j circuit breaker
 * and respects {@link MemoryToggleStore} per-session toggles.
 * When the circuit is open or memory is disabled, returns an empty list.
 */
public class CircuitBreakerMemorySearchManager implements MemorySearchManager {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerMemorySearchManager.class);

    private final MemorySearchManager delegate;
    private final CircuitBreaker circuitBreaker;
    private final MemoryToggleStore toggleStore;

    public CircuitBreakerMemorySearchManager(MemorySearchManager delegate, MemoryToggleStore toggleStore) {
        this.delegate = delegate;
        this.toggleStore = toggleStore;
        this.circuitBreaker = CircuitBreaker.of("memory-search",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .build());
    }

    public CircuitBreakerMemorySearchManager(MemorySearchManager delegate,
                                              MemoryToggleStore toggleStore,
                                              CircuitBreaker circuitBreaker) {
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

        try {
            return circuitBreaker.executeSupplier(() -> delegate.search(query, options));
        } catch (Exception e) {
            log.warn("Memory search failed (circuit breaker state: {}): {}",
                    circuitBreaker.getState(), e.getMessage());
            return List.of();
        }
    }

    public CircuitBreaker.State circuitBreakerState() {
        return circuitBreaker.getState();
    }
}

package io.jaiclaw.memory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link MemoryCircuitBreaker} implementation using native Resilience4j.
 * Lightweight — no Spring Cloud dependency required.
 */
public class NativeResilience4jCircuitBreaker implements MemoryCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public NativeResilience4jCircuitBreaker() {
        this(CircuitBreaker.of("memory-search",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .build()));
    }

    public NativeResilience4jCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
        try {
            return circuitBreaker.executeSupplier(toRun);
        } catch (Exception e) {
            return fallback.apply(e);
        }
    }

    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }
}

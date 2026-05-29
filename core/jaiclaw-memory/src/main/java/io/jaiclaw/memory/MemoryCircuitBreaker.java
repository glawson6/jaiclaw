package io.jaiclaw.memory;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Abstraction for circuit breaker behavior in memory search.
 * Supports both native Resilience4j and Spring Cloud CircuitBreaker implementations.
 */
@FunctionalInterface
public interface MemoryCircuitBreaker {

    /**
     * Execute the supplier with circuit breaker protection.
     *
     * @param toRun    the operation to execute
     * @param fallback function to call if the operation fails or the circuit is open
     * @param <T>      return type
     * @return the result from either the supplier or the fallback
     */
    <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback);
}

package io.jaiclaw.memory;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link MemoryCircuitBreaker} implementation using Spring Cloud CircuitBreaker.
 * Provides automatic Micrometer integration and YAML-driven configuration
 * when {@code spring-cloud-starter-circuitbreaker-resilience4j} is on the classpath.
 */
public class SpringCloudCircuitBreaker implements MemoryCircuitBreaker {

    private final org.springframework.cloud.client.circuitbreaker.CircuitBreaker circuitBreaker;

    public SpringCloudCircuitBreaker(
            org.springframework.cloud.client.circuitbreaker.CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
        return circuitBreaker.run(toRun, fallback);
    }
}

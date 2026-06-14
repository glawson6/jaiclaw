package io.jaiclaw.core.agent;

/**
 * Thrown by {@link AgentMindMemoryProvider#saveMemory} when a write is rejected
 * because the incoming document's {@code version()} is not strictly greater
 * than the stored version. Optimistic CAS — losing writers must reload,
 * consolidate, and retry.
 */
public class StaleMemoryVersionException extends RuntimeException {

    private final long expectedMin;
    private final long actual;

    public StaleMemoryVersionException(long expectedMin, long actual) {
        super("Stale Memory write: incoming version " + actual
                + " must be > stored version " + (expectedMin - 1));
        this.expectedMin = expectedMin;
        this.actual = actual;
    }

    public long expectedMin() { return expectedMin; }
    public long actual() { return actual; }
}

package io.jaiclaw.core.agent;

/**
 * Thrown by {@link SoulProvider#saveSoul} when a write is rejected because the
 * incoming Soul's {@code version()} is not strictly greater than the stored
 * version. Implements optimistic CAS — losing writers must reload, merge, and
 * retry.
 */
public class StaleSoulVersionException extends RuntimeException {

    private final long expectedMin;
    private final long actual;

    public StaleSoulVersionException(long expectedMin, long actual) {
        super("Stale Soul write: incoming version " + actual
                + " must be > stored version " + (expectedMin - 1));
        this.expectedMin = expectedMin;
        this.actual = actual;
    }

    public long expectedMin() { return expectedMin; }
    public long actual() { return actual; }
}

package io.jaiclaw.core.agent;

/**
 * Thrown by the Tendencies store when a write is rejected because the
 * incoming record's {@code version()} is not strictly greater than the
 * stored version. Optimistic CAS — losing writers must reload, merge, and
 * retry. The dialectic pipeline normally avoids this by serialising writes
 * for the same {@code (tenantId, userKey)} through the striped executor.
 */
public class StaleTendenciesVersionException extends RuntimeException {

    private final long expectedMin;
    private final long actual;

    public StaleTendenciesVersionException(long expectedMin, long actual) {
        super("Stale Tendencies write: incoming version " + actual
                + " must be > stored version " + (expectedMin - 1));
        this.expectedMin = expectedMin;
        this.actual = actual;
    }

    public long expectedMin() { return expectedMin; }
    public long actual() { return actual; }
}

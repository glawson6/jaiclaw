package io.jaiclaw.agentmind.tendencies.cost;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tenant daily token cap + circuit breaker for the dialectic pipeline.
 * Analysis §9 risk 1 mitigation.
 *
 * <p>Wraps a per-tenant tokens-spent counter that resets at the start of
 * each UTC day. {@link #canSpend(String, long)} returns {@code false} once
 * the day's cap is reached; the trigger uses this to skip the dialectic
 * call rather than incur a budget overrun. {@link #recordSpend} is called
 * after each pass with the estimated token cost.
 *
 * <p>Plan §8 task 3.11.
 */
public class TendenciesTokenBudget {

    private final long dailyCap;
    private final Clock clock;
    private final ConcurrentHashMap<String, DailyCounter> counters = new ConcurrentHashMap<>();

    public TendenciesTokenBudget(long dailyCap) {
        this(dailyCap, Clock.systemUTC());
    }

    public TendenciesTokenBudget(long dailyCap, Clock clock) {
        if (dailyCap < 1) throw new IllegalArgumentException("dailyCap must be >= 1");
        this.dailyCap = dailyCap;
        this.clock = clock;
    }

    /**
     * Return {@code true} if the tenant has at least {@code estimatedTokens}
     * remaining today.
     */
    public boolean canSpend(String tenantId, long estimatedTokens) {
        DailyCounter c = counters.computeIfAbsent(tenantId, k -> new DailyCounter());
        synchronized (c) {
            c.maybeReset(today());
            return c.spent + estimatedTokens <= dailyCap;
        }
    }

    /** Atomically add {@code tokens} to the tenant's daily counter. */
    public void recordSpend(String tenantId, long tokens) {
        DailyCounter c = counters.computeIfAbsent(tenantId, k -> new DailyCounter());
        synchronized (c) {
            c.maybeReset(today());
            c.spent += tokens;
        }
    }

    /** Snapshot for the Actuator endpoint. */
    public Snapshot snapshot(String tenantId) {
        DailyCounter c = counters.get(tenantId);
        long spent = c == null ? 0L : c.spent;
        return new Snapshot(tenantId, dailyCap, spent, Math.max(0L, dailyCap - spent));
    }

    public long dailyCap() { return dailyCap; }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneId.of("UTC")));
    }

    private static class DailyCounter {
        LocalDate day = null;
        long spent = 0L;

        void maybeReset(LocalDate now) {
            if (!now.equals(day)) {
                day = now;
                spent = 0L;
            }
        }
    }

    public record Snapshot(String tenantId, long dailyCap, long spent, long remaining) {}
}

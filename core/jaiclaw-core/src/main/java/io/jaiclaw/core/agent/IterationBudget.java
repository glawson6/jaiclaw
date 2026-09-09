package io.jaiclaw.core.agent;

import io.jaiclaw.core.api.Experimental;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe countdown of tool-loop iterations available to a single agent run.
 *
 * <p>A budget is a <em>per-run</em> object, not a shared configuration value.
 * {@link ToolLoopConfig#budgetTemplate()} carries the configured size; each run
 * calls {@link #forRun()} to obtain its own independent counter. Parent and child
 * (delegated) runs therefore never contend on the same instance.
 *
 * <p>The loop calls {@link #tryConsume()} once per iteration. When it returns
 * {@code false} the budget is exhausted and the caller is expected to perform a
 * single tool-less final turn rather than abort — an exhausted run should still
 * produce an answer summarising what it managed to do.
 *
 * <p>{@link #refund()} exists for iterations that did not represent real progress
 * (a transport retry of the same model call, for example). Refunds never push the
 * remaining count above the original size.
 *
 * <p>Instances are safe for concurrent use; the counter is an {@link AtomicInteger}
 * and every mutation is a compare-and-set loop.
 *
 * @see ToolLoopConfig
 */
@Experimental
public final class IterationBudget {

    /** Sentinel size meaning "no limit"; {@link #tryConsume()} always succeeds. */
    public static final int UNLIMITED = -1;

    private final int size;
    private final AtomicInteger remaining;

    private IterationBudget(int size) {
        this.size = size;
        this.remaining = new AtomicInteger(Math.max(size, 0));
    }

    /**
     * Creates a budget of {@code size} iterations.
     *
     * @param size number of iterations; values {@code <= 0} yield an
     *             {@linkplain #unlimited() unlimited} budget so that callers
     *             which pass a misconfigured zero do not deadlock the loop
     */
    public static IterationBudget of(int size) {
        return size <= 0 ? unlimited() : new IterationBudget(size);
    }

    /** A budget that never runs out. Used when adopters disable the guard. */
    public static IterationBudget unlimited() {
        return new IterationBudget(UNLIMITED);
    }

    /** True when this budget imposes no limit. */
    public boolean isUnlimited() {
        return size == UNLIMITED;
    }

    /** The configured size, or {@link #UNLIMITED}. */
    public int size() {
        return size;
    }

    /** Iterations still available; {@link Integer#MAX_VALUE} when unlimited. */
    public int remaining() {
        return isUnlimited() ? Integer.MAX_VALUE : remaining.get();
    }

    /** Iterations consumed so far; always {@code 0} when unlimited. */
    public int consumed() {
        return isUnlimited() ? 0 : size - remaining.get();
    }

    /**
     * Consumes one iteration.
     *
     * @return {@code true} if an iteration was available, {@code false} when the
     *         budget is exhausted (the caller should finish with a tool-less turn)
     */
    public boolean tryConsume() {
        if (isUnlimited()) return true;
        while (true) {
            int current = remaining.get();
            if (current <= 0) return false;
            if (remaining.compareAndSet(current, current - 1)) return true;
        }
    }

    /**
     * Returns one iteration to the budget, never exceeding the original size.
     * Intended for retried calls that made no progress.
     */
    public void refund() {
        if (isUnlimited()) return;
        while (true) {
            int current = remaining.get();
            if (current >= size) return;
            if (remaining.compareAndSet(current, current + 1)) return;
        }
    }

    /**
     * Fraction of the budget consumed, in {@code [0.0, 1.0]}. Always {@code 0.0}
     * for an unlimited budget so warning thresholds never fire.
     */
    public double ratioConsumed() {
        if (isUnlimited() || size == 0) return 0.0;
        return (double) consumed() / (double) size;
    }

    /**
     * True once {@link #ratioConsumed()} has reached {@code ratio}. Callers that
     * want a one-shot warning must track "already warned" themselves — this
     * method is a pure predicate and stays true for the rest of the run.
     *
     * @param ratio threshold in {@code (0.0, 1.0]}; values outside that range
     *              never trigger
     */
    public boolean warnAtRatio(double ratio) {
        if (isUnlimited() || ratio <= 0.0 || ratio > 1.0) return false;
        return ratioConsumed() >= ratio;
    }

    /** A fresh, independent budget with this budget's configured size. */
    public IterationBudget forRun() {
        return isUnlimited() ? unlimited() : new IterationBudget(size);
    }

    @Override
    public String toString() {
        return isUnlimited()
                ? "IterationBudget[unlimited]"
                : "IterationBudget[" + remaining.get() + "/" + size + "]";
    }
}

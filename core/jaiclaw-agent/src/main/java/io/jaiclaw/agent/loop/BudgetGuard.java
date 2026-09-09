package io.jaiclaw.agent.loop;

import io.jaiclaw.core.agent.IterationBudget;
import io.jaiclaw.core.api.Experimental;

/**
 * Owns a single run's {@link IterationBudget} and the one-shot "wrap up" warning
 * that goes with it.
 *
 * <p>The loop calls {@link #tryConsume()} at the top of every iteration. When it
 * returns false the budget is spent and the loop performs one final model call
 * with no tools attached, so the run ends with a real answer rather than a bare
 * "max iterations reached" string.
 *
 * <p>When consumption crosses the configured warning ratio, {@link #takeWarning()}
 * returns a notice exactly once. The loop appends it to the next tool result,
 * which is how the model learns it is running out of room — the Hermes pattern of
 * telling the model about its own budget rather than silently truncating it.
 *
 * <p>Not thread-safe by contract, though the underlying budget is: one instance
 * belongs to one run.
 *
 * <p>Phase 1 of the 1.2.0 plan.
 */
@Experimental
public final class BudgetGuard {

    private final IterationBudget budget;
    private final double warningRatio;
    private boolean warned;

    public BudgetGuard(IterationBudget budget, double warningRatio) {
        this.budget = budget == null ? IterationBudget.unlimited() : budget;
        this.warningRatio = warningRatio;
    }

    /** The run's budget. */
    public IterationBudget budget() {
        return budget;
    }

    /**
     * Consumes one iteration.
     *
     * @return false when the budget is exhausted and the loop should finish
     */
    public boolean tryConsume() {
        return budget.tryConsume();
    }

    /** Returns an unused iteration; see {@link IterationBudget#refund()}. */
    public void refund() {
        budget.refund();
    }

    /** True once, when the warning ratio has just been crossed. */
    public boolean shouldWarn() {
        return !warned && budget.warnAtRatio(warningRatio);
    }

    /**
     * Marks the warning as delivered and returns its text. Returns null if the
     * warning has already been taken or is not yet due, so callers can use this
     * as a single guarded call.
     */
    public String takeWarning() {
        if (!shouldWarn()) return null;
        warned = true;
        return notice(budget.remaining());
    }

    /** Iterations still available. */
    public int remaining() {
        return budget.remaining();
    }

    /** Iterations consumed. */
    public int consumed() {
        return budget.consumed();
    }

    /** The configured budget size, or {@link IterationBudget#UNLIMITED}. */
    public int size() {
        return budget.size();
    }

    /** The checkpoint notice appended to the next tool result at the warn ratio. */
    public static String notice(int remaining) {
        return "[budget] About " + remaining + " tool iteration"
                + (remaining == 1 ? "" : "s")
                + " remain for this task. Wrap up: finish the work, or persist your progress "
                + "and summarise what is left.";
    }

    /**
     * The instruction attached to the final, tool-less turn once the budget is
     * spent. The model still answers; it simply cannot call more tools.
     */
    public static String exhaustedInstruction() {
        return "[budget] The tool iteration budget for this task is exhausted. Do not request "
                + "further tool calls. Summarise what you accomplished, what you learned, and "
                + "what remains to be done.";
    }
}

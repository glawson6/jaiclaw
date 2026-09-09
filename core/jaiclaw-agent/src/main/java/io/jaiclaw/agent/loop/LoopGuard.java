package io.jaiclaw.agent.loop;

import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.agent.ApprovalFloor;
import io.jaiclaw.core.agent.ToolLoopConfig;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.hook.event.BudgetWarningEvent;
import io.jaiclaw.core.hook.event.RepetitionDetectedEvent;

/**
 * The single collaborator {@code ExplicitToolLoop} consults for all Phase 1
 * runtime guards: iteration budget, repetition detection, empty-response
 * detection and approval floors.
 *
 * <p>Bundling them here keeps the loop's own diff to a handful of call sites and
 * gives the guards one place to be constructed, reset and tested. One instance
 * belongs to one run of the loop.
 *
 * <p>Every guard is neutral by default, so a {@link ToolLoopConfig} built with
 * the legacy three-argument constructor produces a {@code LoopGuard} that never
 * intervenes beyond the iteration cap that already existed.
 *
 * <p>Phase 1 of the 1.2.0 plan.
 */
@Experimental
public final class LoopGuard {

    private final ToolLoopConfig config;
    private final BudgetGuard budget;
    private final RepetitionGuard repetition;
    private final EmptyResponseGuard emptyResponse;
    private final AgentHookDispatcher hooks;
    private final String agentId;
    private final String sessionKey;

    private boolean forceFinalTurn;
    private String forcedFinalInstruction;

    public LoopGuard(ToolLoopConfig config, AgentHookDispatcher hooks,
                     String agentId, String sessionKey) {
        this.config = config;
        this.hooks = hooks;
        this.agentId = agentId;
        this.sessionKey = sessionKey;
        this.budget = new BudgetGuard(config.newRunBudget(), config.budgetWarningRatio());
        this.repetition = new RepetitionGuard(config.repetitionThreshold());
        this.emptyResponse = new EmptyResponseGuard();
    }

    /**
     * Consumes one iteration of budget.
     *
     * @return false when the budget is spent and the loop must finish
     */
    public boolean tryConsumeIteration() {
        return budget.tryConsume();
    }

    /**
     * Returns a one-time budget notice to append to the next tool result, or null.
     * Fires {@link BudgetWarningEvent} the first time it returns non-null.
     */
    public String takeBudgetWarning() {
        String notice = budget.takeWarning();
        if (notice != null && hooks != null) {
            hooks.fireVoid(BudgetWarningEvent.of(agentId, sessionKey,
                    budget.consumed(), budget.size(), budget.remaining()));
        }
        return notice;
    }

    /**
     * Evaluates a tool call against the repetition guard.
     *
     * @return the verdict; {@link RepetitionGuard.Verdict#NOTIFY} means the loop
     *         should substitute {@link RepetitionGuard#notice(String)} for the
     *         tool result, {@link RepetitionGuard.Verdict#FORCE_FINAL} means it
     *         should stop calling tools altogether
     */
    public RepetitionGuard.Verdict observeToolCall(String toolName, String arguments) {
        RepetitionGuard.Verdict verdict = repetition.observe(toolName, arguments);
        if (verdict == RepetitionGuard.Verdict.NOTIFY && hooks != null) {
            hooks.fireVoid(RepetitionDetectedEvent.of(agentId, sessionKey,
                    toolName, repetition.repeatCount(), false));
        }
        if (verdict == RepetitionGuard.Verdict.FORCE_FINAL) {
            if (hooks != null) {
                hooks.fireVoid(RepetitionDetectedEvent.of(agentId, sessionKey,
                        toolName, repetition.repeatCount(), true));
            }
            requestFinalTurn(RepetitionGuard.forcedFinalNotice(toolName));
        }
        return verdict;
    }

    /**
     * Records a model response for empty-response detection.
     *
     * @return true when the loop should force a tool-less final turn
     */
    public boolean observeResponse(String text, int toolCalls) {
        if (emptyResponse.observe(text, toolCalls)) {
            requestFinalTurn(EmptyResponseGuard.instruction());
            return true;
        }
        return false;
    }

    /** The approval floor configured for a tool; never null. */
    public ApprovalFloor floorFor(String toolName) {
        return config.floorFor(toolName);
    }

    /**
     * True when a guard has demanded the loop stop calling tools and produce a
     * final answer.
     */
    public boolean finalTurnRequested() {
        return forceFinalTurn;
    }

    /** The instruction to attach to the forced final turn, or null. */
    public String finalTurnInstruction() {
        return forcedFinalInstruction;
    }

    /** Marks that the loop should finish with one tool-less turn. */
    public void requestFinalTurn(String instruction) {
        this.forceFinalTurn = true;
        if (this.forcedFinalInstruction == null) this.forcedFinalInstruction = instruction;
    }

    /** Iterations consumed by this run. */
    public int iterationsUsed() {
        return budget.consumed();
    }

    /** Iterations still available. */
    public int remaining() {
        return budget.remaining();
    }
}

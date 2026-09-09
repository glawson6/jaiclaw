package io.jaiclaw.core.hook.event;

import io.jaiclaw.core.api.Experimental;

import java.time.Instant;

/**
 * Fired once per run when the tool loop's {@code IterationBudget} crosses the
 * configured warning ratio (default 90% consumed). The loop also injects a
 * one-time "wrap up" notice into the next tool result so the model itself can
 * react; this event is the observability counterpart for dashboards and audit.
 *
 * <p>Fired at most once per run — the loop tracks that it has warned. A run that
 * finishes before the ratio is reached never fires it.
 *
 * <p>Phase 1 of the 1.2.0 plan (runtime guards).
 *
 * @param agentId    agent whose run is approaching its budget
 * @param sessionKey session the run belongs to
 * @param timestamp  when the threshold was crossed
 * @param consumed   iterations consumed at the moment of warning
 * @param size       the run's configured budget size
 * @param remaining  iterations still available
 */
@Experimental
public record BudgetWarningEvent(
        String agentId,
        String sessionKey,
        Instant timestamp,
        int consumed,
        int size,
        int remaining
) implements HookEvent {

    public static BudgetWarningEvent of(String agentId, String sessionKey,
                                        int consumed, int size, int remaining) {
        return new BudgetWarningEvent(agentId, sessionKey, Instant.now(), consumed, size, remaining);
    }
}

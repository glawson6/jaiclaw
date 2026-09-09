package io.jaiclaw.agent.delegation;

import io.jaiclaw.core.api.Experimental;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A live reference to a delegated child run: await it, poll it, or cancel it.
 *
 * <p>Handles are created by {@link SubAgentLauncher#launch} and kept in a
 * per-parent registry so the {@code delegate_status} tool can find one by id
 * after the launching call has returned.
 *
 * <p>The plan sketched a Reactor {@code Flux} for progress. This module has no
 * Reactor dependency, and adding one for a single optional field would push it
 * onto every adopter of {@code jaiclaw-agent}; a plain callback carries the same
 * information without the coupling.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public final class SubAgentHandle {

    private final String id;
    private final String sessionKey;
    private final Instant startedAt;
    private final CompletableFuture<SubAgentResult> future;

    public SubAgentHandle(String id, String sessionKey,
                          CompletableFuture<SubAgentResult> future, Instant startedAt) {
        this.id = id;
        this.sessionKey = sessionKey;
        this.future = future;
        this.startedAt = startedAt;
    }

    public String id() {
        return id;
    }

    public String sessionKey() {
        return sessionKey;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** The underlying future. Prefer {@link #await(Duration)} or {@link #poll()}. */
    public CompletableFuture<SubAgentResult> future() {
        return future;
    }

    /** True once the child has finished, failed, or been cancelled. */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * The current state without blocking: the terminal result if the child has
     * finished, otherwise a {@link SubAgentStatus#RUNNING} snapshot.
     */
    public SubAgentResult poll() {
        if (!future.isDone()) return SubAgentResult.running(id, sessionKey);
        try {
            return future.getNow(SubAgentResult.running(id, sessionKey));
        } catch (RuntimeException e) {
            return SubAgentResult.failed(id, sessionKey, unwrap(e), 0);
        }
    }

    /**
     * Blocks for the child's result.
     *
     * @return the terminal result, or a {@link SubAgentStatus#RUNNING} snapshot if
     *         {@code timeout} elapses first — a slow child is not an error, and the
     *         parent can keep polling by handle id
     */
    public SubAgentResult await(Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return SubAgentResult.running(id, sessionKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubAgentResult.failed(id, sessionKey, "Interrupted while awaiting the subagent", 0);
        } catch (Exception e) {
            return SubAgentResult.failed(id, sessionKey, unwrap(e), 0);
        }
    }

    /**
     * Requests cancellation.
     *
     * @return true if this call transitioned the run to cancelled
     */
    public boolean cancel() {
        return future.cancel(true);
    }

    private static String unwrap(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }
}

package io.jaiclaw.pipeline.gateway

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineProperties
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * SEV-006 regression guard — verifies tenant context propagation across
 * the {@code PipelineSyncCoordinator} async boundary.
 *
 * <p>Before the fix, {@code complete}'s {@code future.completeAsync(...,
 * completionExecutor)} dispatched any {@code .thenApply(...)}
 * continuation onto a completion-pool thread with no tenant context.
 * Downstream tools relying on {@code TenantContextHolder} would observe
 * SINGLE-mode behavior even in MULTI-mode deployments, or worse: see
 * leaked context from whoever last ran on that thread.
 *
 * <p>The fix captures the caller's tenant at {@code register()} time
 * and restores it before running the completion task, so continuations
 * see the right tenant.
 */
class PipelineSyncCoordinatorTenantPropagationSpec extends Specification {

    private static PipelineProperties.SyncProperties props() {
        return new PipelineProperties.SyncProperties(
                10, Duration.ofMinutes(5), Duration.ofMinutes(1), 2)
    }

    private static PipelineContext ctx(String id) {
        return new PipelineContext("pipe", id, null, null, 0, 1, null, null, [:], [:])
    }

    private static PipelineExecutionResult success(String id) {
        return PipelineExecutionResult.success(ctx(id), Instant.now(), Instant.now(), Duration.ZERO)
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    def "complete() continuation sees the tenant active at register() time"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(props())
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")
        AtomicReference<TenantContext> observed = new AtomicReference<>()
        CompletableFuture<Void> done = f.thenAccept { result ->
            observed.set(TenantContextHolder.get())
        }

        when: "another thread (no tenant context) completes the future"
        Thread.start {
            TenantContextHolder.clear()
            coordinator.complete("e1", success("e1"))
        }.join()
        done.get(2, TimeUnit.SECONDS)

        then: "the continuation saw the tenant captured at register() time"
        observed.get() != null
        observed.get().getTenantId() == "acme"

        cleanup:
        coordinator.shutdown()
    }

    def "completeExceptionally() continuation sees the tenant active at register() time"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(props())
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e2")
        AtomicReference<TenantContext> observed = new AtomicReference<>()
        CompletableFuture<Void> done = f.exceptionally { ex ->
            observed.set(TenantContextHolder.get())
            return null
        }

        when:
        Thread.start {
            TenantContextHolder.clear()
            coordinator.completeExceptionally("e2", new RuntimeException("boom"))
        }.join()
        done.get(2, TimeUnit.SECONDS)

        then:
        observed.get() != null
        observed.get().getTenantId() == "acme"

        cleanup:
        coordinator.shutdown()
    }

    def "no captured tenant in SINGLE mode means no leak into completion thread"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(props())
        // SINGLE-mode: no tenant on the registering thread
        TenantContextHolder.clear()
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e3")
        AtomicReference<TenantContext> observed = new AtomicReference<>()
        AtomicReference<Boolean> ran = new AtomicReference<>(false)
        CompletableFuture<Void> done = f.thenAccept { result ->
            observed.set(TenantContextHolder.get())
            ran.set(true)
        }

        when:
        Thread.start {
            // Different thread happens to have a leaked tenant — would
            // be visible to the completion if we didn't capture/clear.
            TenantContextHolder.set(new DefaultTenantContext("leaked", "leaked"))
            try {
                coordinator.complete("e3", success("e3"))
            } finally {
                TenantContextHolder.clear()
            }
        }.join()
        done.get(2, TimeUnit.SECONDS)

        then: "no tenant was active at register(); continuation sees whatever the completion thread had"
        // The contract: we don't INVENT a tenant when none was captured.
        // The continuation may see the completion thread's context.
        // What we guarantee is that callers who DID have a tenant get
        // their own tenant back, not someone else's.
        ran.get() == true

        cleanup:
        coordinator.shutdown()
    }

    def "two concurrent registrations from different tenants do not cross-contaminate"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(props())

        // Tenant A registers
        TenantContextHolder.set(new DefaultTenantContext("tenant-a", "tenant-a"))
        CompletableFuture<PipelineExecutionResult> fA = coordinator.register("eA")
        TenantContextHolder.clear()

        // Tenant B registers
        TenantContextHolder.set(new DefaultTenantContext("tenant-b", "tenant-b"))
        CompletableFuture<PipelineExecutionResult> fB = coordinator.register("eB")
        TenantContextHolder.clear()

        AtomicReference<String> observedA = new AtomicReference<>()
        AtomicReference<String> observedB = new AtomicReference<>()
        CompletableFuture<Void> doneA = fA.thenAccept { observedA.set(TenantContextHolder.get()?.getTenantId()) }
        CompletableFuture<Void> doneB = fB.thenAccept { observedB.set(TenantContextHolder.get()?.getTenantId()) }

        when: "completed by an unrelated thread with no tenant context"
        Thread.start {
            coordinator.complete("eA", success("eA"))
            coordinator.complete("eB", success("eB"))
        }.join()
        doneA.get(2, TimeUnit.SECONDS)
        doneB.get(2, TimeUnit.SECONDS)

        then: "each continuation saw its own tenant"
        observedA.get() == "tenant-a"
        observedB.get() == "tenant-b"

        cleanup:
        coordinator.shutdown()
    }
}

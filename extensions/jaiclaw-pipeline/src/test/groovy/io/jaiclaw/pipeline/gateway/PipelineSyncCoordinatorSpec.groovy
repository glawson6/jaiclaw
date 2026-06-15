package io.jaiclaw.pipeline.gateway

import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineProperties
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class PipelineSyncCoordinatorSpec extends Specification {

    private static PipelineProperties.SyncProperties props(int max, Duration ttl, Duration sweep) {
        return new PipelineProperties.SyncProperties(max, ttl, sweep, 2)
    }

    private static PipelineContext ctx(String id = "exec-1") {
        return new PipelineContext("pipe", id, null, null, 0, 1, null, null, [:], [:])
    }

    private static PipelineExecutionResult success(String id) {
        return PipelineExecutionResult.success(ctx(id), Instant.now(), Instant.now(), Duration.ZERO)
    }

    def "register returns a future and tracks the entry"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))

        when:
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")

        then:
        f != null
        !f.isDone()
        coordinator.pendingCount() == 1

        cleanup:
        coordinator.shutdown()
    }

    def "complete delivers the result and clears the entry"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")

        when:
        coordinator.complete("e1", success("e1"))
        PipelineExecutionResult result = f.get(1, TimeUnit.SECONDS)

        then:
        result.executionId() == "e1"
        result.status() == ExecutionStatus.SUCCESS
        coordinator.pendingCount() == 0

        cleanup:
        coordinator.shutdown()
    }

    def "complete is a no-op for an unregistered execution id"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))

        when:
        coordinator.complete("never-registered", success("never-registered"))

        then:
        noExceptionThrown()
        coordinator.pendingCount() == 0

        cleanup:
        coordinator.shutdown()
    }

    def "double-complete is safe and second call is ignored"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")

        when:
        coordinator.complete("e1", success("e1"))
        coordinator.complete("e1", success("e1"))

        then:
        f.get(1, TimeUnit.SECONDS).executionId() == "e1"
        coordinator.pendingCount() == 0

        cleanup:
        coordinator.shutdown()
    }

    def "completeExceptionally propagates the cause"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")
        Throwable cause = new PipelineOrphanException("e1", "boom")

        when:
        coordinator.completeExceptionally("e1", cause)
        f.get(1, TimeUnit.SECONDS)

        then:
        ExecutionException ex = thrown()
        ex.cause.is(cause)
        coordinator.pendingCount() == 0

        cleanup:
        coordinator.shutdown()
    }

    def "register at capacity returns an already-failed future"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(2, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        coordinator.register("a")
        coordinator.register("b")

        when:
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("c")

        then:
        f.isCompletedExceptionally()
        coordinator.pendingCount() == 2
        when:
        f.get(1, TimeUnit.SECONDS)

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof PipelineCapacityException
        ((PipelineCapacityException) ex.cause).executionId() == "c"
        ((PipelineCapacityException) ex.cause).capacity() == 2

        cleanup:
        coordinator.shutdown()
    }

    def "blank or null executionId is rejected"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                PipelineProperties.SyncProperties.DEFAULT)

        when:
        CompletableFuture<PipelineExecutionResult> f = coordinator.register(null)

        then:
        f.isCompletedExceptionally()

        when:
        CompletableFuture<PipelineExecutionResult> f2 = coordinator.register("")

        then:
        f2.isCompletedExceptionally()

        cleanup:
        coordinator.shutdown()
    }

    def "orphan sweep reaps futures older than ttl"() {
        given: "a coordinator with a tiny TTL and a fast sweep"
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMillis(100), Duration.ofMillis(50)))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("orphan-1")

        expect: "future eventually completes exceptionally with PipelineOrphanException"
        PollingConditions cond = new PollingConditions(timeout: 3.0, initialDelay: 0.05)
        cond.eventually {
            f.isCompletedExceptionally()
        }

        when:
        f.get(1, TimeUnit.SECONDS)

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof PipelineOrphanException
        ((PipelineOrphanException) ex.cause).executionId() == "orphan-1"

        cleanup:
        coordinator.shutdown()
    }

    def "shutdown abandons pending futures with PipelineOrphanException"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(10, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        CompletableFuture<PipelineExecutionResult> f = coordinator.register("e1")

        when:
        coordinator.shutdown()

        then:
        f.isCompletedExceptionally()
        coordinator.pendingCount() == 0

        when:
        f.get(1, TimeUnit.SECONDS)

        then:
        ExecutionException ex = thrown()
        ex.cause instanceof PipelineOrphanException
    }

    def "concurrent register/complete on independent ids do not cross-talk"() {
        given:
        PipelineSyncCoordinator coordinator = new PipelineSyncCoordinator(
                props(100, Duration.ofMinutes(5), Duration.ofMinutes(1)))
        CompletableFuture<PipelineExecutionResult> f1 = coordinator.register("a")
        CompletableFuture<PipelineExecutionResult> f2 = coordinator.register("b")

        when:
        coordinator.complete("a", success("a"))

        then: "only f1 completes"
        f1.get(1, TimeUnit.SECONDS).executionId() == "a"
        !f2.isDone()
        coordinator.pendingCount() == 1

        when:
        coordinator.complete("b", success("b"))

        then:
        f2.get(1, TimeUnit.SECONDS).executionId() == "b"
        coordinator.pendingCount() == 0

        cleanup:
        coordinator.shutdown()
    }
}

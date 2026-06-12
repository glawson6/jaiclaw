package io.jaiclaw.kanban.web

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.KanbanProperties
import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.model.BoardSnapshot
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.BoardSnapshotService
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit-level coverage for {@link KanbanEventBroadcaster}: registration,
 * deregistration via emitter completion, the per-tenant connection cap, and
 * the fan-out path on {@code TaskStateChanged}.
 *
 * <p>Spring-context coverage (end-to-end SSE over real Tomcat) lives in
 * {@code events/KanbanEventControllerSpec}.
 */
class KanbanEventBroadcasterSpec extends Specification {

    BoardSnapshotService snapshotService = Mock()
    TenantGuard tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
    KanbanProperties.Sse sseProps = new KanbanProperties.Sse(true, 25, 2)
    KanbanEventBroadcaster broadcaster

    def setup() {
        broadcaster = new KanbanEventBroadcaster(snapshotService, tenantGuard, sseProps)
    }

    private TaskRecord card() {
        new TaskRecord("t1", "T1", null, TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.now(), null, null, null,
                "b1", "backlog", null, 0L, 0, null)
    }

    private TaskStateChanged event() {
        new TaskStateChanged(
                new TransitionRecord("t1", "b1", "backlog", "drafting",
                        "START", "alice", "default", Instant.now()),
                card().withState("drafting"))
    }

    def "register sends an initial snapshot event"() {
        given:
        def emitter = Mock(SseEmitter)
        snapshotService.snapshot("b1") >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))

        when:
        def result = broadcaster.register("b1", emitter)

        then:
        result.is(emitter)
        1 * emitter.send({ it != null })   // an SseEvent payload
        1 * emitter.onCompletion(_)
        1 * emitter.onTimeout(_)
        1 * emitter.onError(_)
    }

    def "state-changed events fan out to all matching emitters"() {
        given:
        snapshotService.snapshot(_) >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))
        def emitterA = Mock(SseEmitter)
        def emitterB = Mock(SseEmitter)
        broadcaster.register("b1", emitterA)
        broadcaster.register("b1", emitterB)

        when:
        broadcaster.onTaskStateChanged(event())

        then: "the state-changed send fires on each emitter (snapshots from register() landed in `given:` and aren't counted)"
        1 * emitterA.send(_)
        1 * emitterB.send(_)
    }

    def "state-changed for a different board is not forwarded"() {
        given:
        snapshotService.snapshot(_) >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))
        def emitter = Mock(SseEmitter)
        broadcaster.register("b1", emitter)

        when:
        broadcaster.onTaskStateChanged(new TaskStateChanged(
                new TransitionRecord("t9", "other-board", "a", "b",
                        "GO", null, "default", Instant.now()),
                card()))

        then: "no send fires in the when block — only the snapshot send from register() ran earlier"
        0 * emitter.send(_)
    }

    def "max-connections cap returns null after the limit"() {
        given:
        snapshotService.snapshot(_) >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))

        when: "max=2 allows two registrations"
        def a = broadcaster.register("b1", Mock(SseEmitter))
        def b = broadcaster.register("b1", Mock(SseEmitter))

        then:
        a != null
        b != null

        when: "the third is rejected"
        def c = broadcaster.register("b1", Mock(SseEmitter))

        then:
        c == null
        broadcaster.totalConnections() == 2
    }

    def "deregistration via onCompletion frees a connection slot"() {
        given: "an emitter that captures its onCompletion callback when register() wires it"
        snapshotService.snapshot(_) >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))
        def captured = new CopyOnWriteArrayList<Runnable>()
        def emitter = Mock(SseEmitter)
        emitter.onCompletion(_) >> { args -> captured.add((Runnable) args[0]); null }

        when:
        broadcaster.register("b1", emitter)

        then:
        broadcaster.totalConnections() == 1
        captured.size() == 1

        when: "the emitter completes"
        captured.first().run()

        then:
        broadcaster.totalConnections() == 0

        when: "a new connection is allowed again under the cap"
        def next = broadcaster.register("b1", Mock(SseEmitter))

        then:
        next != null
    }

    def "stop completes all in-flight emitters and clears the registry"() {
        given:
        snapshotService.snapshot(_) >> Optional.of(
                new BoardSnapshot("b1", "B1", Instant.now(), [], 0))
        def emitter = Mock(SseEmitter)
        broadcaster.register("b1", emitter)
        broadcaster.start()

        when:
        broadcaster.stop()

        then:
        1 * emitter.complete()
        broadcaster.totalConnections() == 0
        !broadcaster.isRunning()
    }
}

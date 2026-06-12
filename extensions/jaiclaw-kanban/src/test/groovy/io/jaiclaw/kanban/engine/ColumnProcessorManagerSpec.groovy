package io.jaiclaw.kanban.engine

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.events.KanbanHookFirer
import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.idempotency.EffectLedger
import io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.ProcessorDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.kanban.state.TransitionGraphStateEngine
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

import java.util.concurrent.Executor

class ColumnProcessorManagerSpec extends Specification {

    @TempDir
    Path tempDir
    TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)
    TaskStore taskStore
    KanbanBoardService boardService
    TransitionHistory history = new TransitionHistory(50)
    TaskTransitionService transitionService
    // Synchronous executor — runs the processor on the calling thread, so
    // event delivery → processor run → onSuccess transition is a single
    // deterministic chain.
    Executor sync = { Runnable r -> r.run() } as Executor

    def setup() {
        taskStore = new JsonFileTaskStore(tempDir, guard)
        boardService = new KanbanBoardService(guard)
        boardService.cache(fixtureBoard("SUBMIT", "BLOCK"))
        def engine = new TransitionGraphStateEngine()
        def publisher = { Object _ignored -> } as ApplicationEventPublisher
        transitionService = new TaskTransitionService(
                taskStore, boardService, engine, history,
                publisher, new KanbanHookFirer(null), guard)
    }

    private BoardDefinition fixtureBoard(String onSuccess, String onFailure) {
        // onSuccess/onFailure are transition EVENT NAMES (not column ids)
        // fired into the state engine on processor completion.
        def processor = new ProcessorDefinition("agent",
                "Draft {{name}}", onSuccess, onFailure,
                true, "fail", 3, [:])
        new BoardDefinition("b1", "B1", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog",  TaskStatus.QUEUED,  null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, null, false, null, processor),
                new ColumnDefinition("review",   "Review",   TaskStatus.RUNNING, null, false, null, null),
                new ColumnDefinition("blocked",  "Blocked",  TaskStatus.BLOCKED, null, false, null, null),
                new ColumnDefinition("done",     "Done",     TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START",   [:]),
                new TransitionDefinition("drafting", "review",   "SUBMIT",  [:]),
                new TransitionDefinition("drafting", "blocked",  "BLOCK",   [:]),
                new TransitionDefinition("review",   "done",     "APPROVE", [:]),
                new TransitionDefinition("blocked",  "drafting", "UNBLOCK", [:]),
        ])
    }

    private AgentColumnProcessor processor(Function<TaskRecord, String> runner) {
        new AgentColumnProcessor(runner, new IdempotencyKeyBuilder(history),
                new EffectLedger(tempDir.resolve("ledger")))
    }

    def "card entering a processor column triggers the runner and routes onSuccess"() {
        given:
        def calls = new AtomicInteger(0)
        Function<TaskRecord, String> runner = { TaskRecord t ->
            calls.incrementAndGet()
            "agent reply"
        }
        def manager = new ColumnProcessorManager(boardService, taskStore,
                transitionService, processor(runner), sync)
        def card = transitionService.createCard("b1", "Hello", "World", [:])
        def task = transitionService.transition(card.id(), "START", "user")
        assert task.accepted()
        def stored = taskStore.findById(card.id()).get()

        when: "fire the @EventListener manually with the post-transition card"
        manager.onTaskStateChanged(new TaskStateChanged(
                new TransitionRecord(card.id(), "b1", "backlog", "drafting",
                        "START", "user", "default", Instant.now()),
                stored))

        then:
        calls.get() == 1
        taskStore.findById(card.id()).get().state() == "review"
        taskStore.findById(card.id()).get().result() == "agent reply"
    }

    def "runner exception routes the card to onFailure and records error"() {
        given:
        Function<TaskRecord, String> runner = { TaskRecord t -> throw new RuntimeException("boom") }
        def manager = new ColumnProcessorManager(boardService, taskStore,
                transitionService, processor(runner), sync)
        def card = transitionService.createCard("b1", "Fail", null, [:])
        transitionService.transition(card.id(), "START", "user")
        def stored = taskStore.findById(card.id()).get()

        when:
        manager.onTaskStateChanged(new TaskStateChanged(
                new TransitionRecord(card.id(), "b1", "backlog", "drafting",
                        "START", "user", "default", Instant.now()),
                stored))

        then:
        def out = taskStore.findById(card.id()).get()
        out.state() == "blocked"
        out.error() == "boom"
    }

    def "cards landing in a column without a processor are ignored"() {
        given:
        def calls = new AtomicInteger(0)
        Function<TaskRecord, String> runner = { TaskRecord t -> calls.incrementAndGet(); "" }
        def manager = new ColumnProcessorManager(boardService, taskStore,
                transitionService, processor(runner), sync)

        when:
        manager.onTaskStateChanged(new TaskStateChanged(
                new TransitionRecord("ghost", "b1", "drafting", "review",
                        "SUBMIT", "user", "default", Instant.now()),
                null))

        then:
        calls.get() == 0
    }
}

package io.jaiclaw.kanban.recovery

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.KanbanProperties
import io.jaiclaw.kanban.events.KanbanHookFirer
import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.ProcessorDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.kanban.state.TransitionGraphStateEngine
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class KanbanRecoveryManagerSpec extends Specification {

    @TempDir
    Path tempDir
    TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)
    TaskStore taskStore
    KanbanBoardService boardService
    TaskTransitionService transitionService
    KanbanRecoveryManager manager
    CopyOnWriteArrayList<TaskStateChanged> published = new CopyOnWriteArrayList<>()
    ApplicationEventPublisher publisher = { Object event ->
        if (event instanceof TaskStateChanged) published.add(event as TaskStateChanged)
    } as ApplicationEventPublisher

    def setup() {
        taskStore = new JsonFileTaskStore(tempDir, guard)
        boardService = new KanbanBoardService(guard)
        boardService.cache(board("fail"))
        def engine = new TransitionGraphStateEngine()
        transitionService = new TaskTransitionService(
                taskStore, boardService, engine, new TransitionHistory(50),
                publisher, new KanbanHookFirer(null), guard)
        def props = new KanbanProperties(true, tempDir.toString(),
                null, null, null, null, null,
                new KanbanProperties.Recovery(true, "fail", 3, "30m"),
                null, null)
        manager = new KanbanRecoveryManager(boardService, taskStore,
                transitionService, props, publisher)
    }

    private BoardDefinition board(String restartPolicy) {
        // onSuccess/onFailure are transition EVENT NAMES, not column ids.
        def processor = new ProcessorDefinition("agent",
                "Draft {{name}}", "SUBMIT", "BLOCK",
                true, restartPolicy, 2, [:])
        new BoardDefinition("b1", "B1", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog",  TaskStatus.QUEUED,    null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING,   null, false, null, processor),
                new ColumnDefinition("review",   "Review",   TaskStatus.RUNNING,   null, false, null, null),
                new ColumnDefinition("blocked",  "Blocked",  TaskStatus.BLOCKED,   null, false, null, null),
                new ColumnDefinition("done",     "Done",     TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START",   [:]),
                new TransitionDefinition("drafting", "review",   "SUBMIT",  [:]),
                new TransitionDefinition("drafting", "blocked",  "BLOCK",   [:]),
                new TransitionDefinition("review",   "done",     "APPROVE", [:]),
                new TransitionDefinition("blocked",  "drafting", "UNBLOCK", [:]),
        ])
    }

    private TaskRecord runningCardOnDrafting() {
        def card = transitionService.createCard("b1", "Stuck", null, [:])
        transitionService.transition(card.id(), "START", "user")
        def cur = taskStore.findById(card.id()).get()
        // Simulate the card being RUNNING (an executor would have flipped it).
        taskStore.save(cur.withStatus(TaskStatus.RUNNING))
        return taskStore.findById(card.id()).get()
    }

    def "FAIL policy routes a RUNNING card to the onFailure column"() {
        given:
        def card = runningCardOnDrafting()

        when:
        int examined = manager.sweepStartup()

        then:
        examined == 1
        taskStore.findById(card.id()).get().state() == "blocked"
    }

    def "policy skips cards on columns without a processor"() {
        given:
        // A RUNNING card sitting on `review` (no processor).
        def card = transitionService.createCard("b1", "Safe", null, [:])
        transitionService.transition(card.id(), "START", "user")
        transitionService.transition(card.id(), "SUBMIT", "user")
        def cur = taskStore.findById(card.id()).get()
        taskStore.save(cur.withStatus(TaskStatus.RUNNING))

        when:
        int examined = manager.sweepStartup()

        then:
        examined == 0
        taskStore.findById(card.id()).get().state() == "review"
    }

    def "REQUEUE policy republishes a RECOVERY TaskStateChanged event"() {
        given:
        boardService.cache(board("requeue"))
        def card = runningCardOnDrafting()
        published.clear()

        when:
        int examined = manager.sweepStartup()

        then:
        examined == 1
        published.size() == 1
        published[0].event() == "RECOVERY"
        published[0].fromState() == "drafting"
        published[0].toState() == "drafting"
        // attempts bumped
        taskStore.findById(card.id()).get().metadata()[KanbanRecoveryManager.ATTEMPT_META_KEY] == "1"
    }

    def "REQUEUE escalates to FAIL after maxAttempts"() {
        given:
        boardService.cache(board("requeue"))
        def card = runningCardOnDrafting()
        // Pre-mark as having hit maxAttempts (=2 in the fixture).
        def updated = taskStore.findById(card.id()).get()
        def rebuilt = new TaskRecord(updated.id(), updated.name(), updated.description(),
                updated.status(), updated.deliveryState(), updated.result(), updated.error(),
                updated.flowId(), [(KanbanRecoveryManager.ATTEMPT_META_KEY): "2"],
                updated.createdAt(), updated.startedAt(), updated.completedAt(), updated.tenantId(),
                updated.boardId(), updated.state(), updated.assignee(),
                updated.version(), updated.orderIndex(), updated.idempotencyKey())
        taskStore.save(rebuilt)
        published.clear()

        when:
        manager.sweepStartup()

        then: "the FAIL fallback fires BLOCK rather than another RECOVERY"
        published*.event() == ["BLOCK"]
        taskStore.findById(card.id()).get().state() == "blocked"
    }

    def "MANUAL policy marks the card with interrupted metadata"() {
        given:
        boardService.cache(board("manual"))
        def card = runningCardOnDrafting()

        when:
        manager.sweepStartup()

        then:
        def out = taskStore.findById(card.id()).get()
        out.metadata()[KanbanRecoveryManager.INTERRUPTED_META_KEY] == "true"
        out.state() == "drafting"
    }

    def "sweepStale only touches cards older than the timeout"() {
        given:
        // Two cards: one with a recent startedAt, one with an ancient one.
        def recent = transitionService.createCard("b1", "Recent", null, [:])
        transitionService.transition(recent.id(), "START", "user")
        def recentNow = taskStore.findById(recent.id()).get()
        taskStore.save(new TaskRecord(recentNow.id(), recentNow.name(), recentNow.description(),
                TaskStatus.RUNNING, TaskDeliveryState.PENDING, null, null, null,
                recentNow.metadata(), recentNow.createdAt(),
                Instant.now(), null, recentNow.tenantId(),
                recentNow.boardId(), recentNow.state(), recentNow.assignee(),
                recentNow.version(), recentNow.orderIndex(), recentNow.idempotencyKey()))

        def ancient = transitionService.createCard("b1", "Ancient", null, [:])
        transitionService.transition(ancient.id(), "START", "user")
        def ancientNow = taskStore.findById(ancient.id()).get()
        taskStore.save(new TaskRecord(ancientNow.id(), ancientNow.name(), ancientNow.description(),
                TaskStatus.RUNNING, TaskDeliveryState.PENDING, null, null, null,
                ancientNow.metadata(), ancientNow.createdAt(),
                Instant.now().minusSeconds(3600), null, ancientNow.tenantId(),
                ancientNow.boardId(), ancientNow.state(), ancientNow.assignee(),
                ancientNow.version(), ancientNow.orderIndex(), ancientNow.idempotencyKey()))

        when:
        int examined = manager.sweepStale(java.time.Duration.ofMinutes(10))

        then:
        examined == 1
        taskStore.findById(recent.id()).get().state() == "drafting"
        taskStore.findById(ancient.id()).get().state() == "blocked"
    }
}

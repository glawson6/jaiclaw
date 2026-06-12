package io.jaiclaw.kanban.e2e

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.KanbanProperties
import io.jaiclaw.kanban.events.KanbanHookFirer
import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.loader.BoardFileLoader
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.kanban.state.TransitionGraphStateEngine
import io.jaiclaw.kanban.validation.BoardValidator
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.DefaultResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Phase 1 end-to-end coverage for the Kanban core engine. Wires the
 * board service, transition service, history, state engine, hook firer,
 * and a real on-disk {@link JsonFileTaskStore} together — no Spring
 * application context — and proves the happy path plus the four
 * Definition-of-Done assertions in the implementation plan §6.2.
 *
 * <p>Spring context-loaded coverage (auto-config, properties binding) lands
 * in Phase 2's {@code KanbanSurfacesE2ESpec} once a web app is on the
 * classpath; running a {@code @SpringBootTest} just to verify wiring
 * without any surfaces gives nothing the wiring itself doesn't already
 * prove here.
 */
class KanbanCoreE2ESpec extends Specification {

    @TempDir
    Path storageDir

    def "transitions a card through the fixture board end-to-end"() {
        given: "the shared fixture board loaded from classpath"
        def loader = new BoardFileLoader(new DefaultResourceLoader())
        List<BoardDefinition> boards = loader.loadAll([
                "classpath:boards/e2e-content-review.yaml"])
        boards.size() == 1
        def validator = new BoardValidator()
        validator.validateOrThrow(boards)

        and: "wiring identical to KanbanAutoConfiguration"
        def tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
        def boardService = new KanbanBoardService(tenantGuard)
        boardService.registerAll(boards)
        def engine = new TransitionGraphStateEngine()
        def props = new KanbanProperties(true, null, null, null, null, null, null, null, null)
        def history = new TransitionHistory(props.history().maxPerBoard())
        def hookFirer = new KanbanHookFirer(null) // no plugin-sdk in test scope
        def taskStore = new JsonFileTaskStore(storageDir, tenantGuard)
        List<TaskStateChanged> observed = new CopyOnWriteArrayList<>()
        def publisher = { event ->
            if (event instanceof TaskStateChanged) observed.add(event)
        } as ApplicationEventPublisher
        def transitionService = new TaskTransitionService(
                taskStore, boardService, engine, history, publisher, hookFirer, tenantGuard)

        when: "a card is created on the board"
        def card = transitionService.createCard("e2e-content-review",
                "Q3 Blog Post", "Long-form blog post about Q3 launch", [:])

        then:
        card.boardId() == "e2e-content-review"
        card.state() == "backlog"
        card.status() == TaskStatus.QUEUED
        observed.size() == 1
        observed[0].event() == "CREATE"
        observed[0].toState() == "backlog"

        when: "the card walks the happy path backlog → drafting → review → done"
        def t1 = transitionService.transition(card.id(), "START", "alice")
        def t2 = transitionService.transition(card.id(), "SUBMIT", "alice")
        def t3 = transitionService.transition(card.id(), "APPROVE", "bob")

        then: "the engine accepted every step"
        t1.accepted() && t1.toState() == "drafting"
        t2.accepted() && t2.toState() == "review"
        t3.accepted() && t3.toState() == "done"

        and: "events fired in order with full payload (boardId, taskId, tenantId, from/to, event, actor)"
        observed.size() == 4
        observed*.event() == ["CREATE", "START", "SUBMIT", "APPROVE"]
        observed.tail()*.actor() == ["alice", "alice", "bob"]
        observed.every { it.boardId() == "e2e-content-review" && it.taskId() == card.id() }
        observed.every { it.transition().tenantId() == TenantProperties.DEFAULT.defaultTenantId() }

        and: "the persisted card reflects the final state and version >= 3 (one bump per transition)"
        def stored = taskStore.findById(card.id()).get()
        stored.state() == "done"
        stored.status() == TaskStatus.SUCCEEDED
        stored.version() >= 3L

        and: "the history records every transition for this board"
        def hist = history.forBoard("e2e-content-review", 10)
        hist.size() == 4
        hist*.event() as Set == ["CREATE", "START", "SUBMIT", "APPROVE"] as Set
    }

    def "atomic flush leaves no tmp file behind after a transition"() {
        given:
        def tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
        def taskStore = new JsonFileTaskStore(storageDir, tenantGuard)
        def boards = new BoardFileLoader(new DefaultResourceLoader())
                .loadAll(["classpath:boards/e2e-content-review.yaml"])
        def boardService = new KanbanBoardService(tenantGuard)
        boardService.registerAll(boards)
        def publisher = { Object _ignored -> } as ApplicationEventPublisher
        def transitionService = new TaskTransitionService(
                taskStore, boardService, new TransitionGraphStateEngine(),
                new TransitionHistory(50), publisher, new KanbanHookFirer(null), tenantGuard)
        def card = transitionService.createCard("e2e-content-review", "X", "Y", [:])

        when:
        transitionService.transition(card.id(), "START", null)

        then:
        Files.exists(storageDir.resolve("tasks.json"))
        !Files.exists(storageDir.resolve("tasks.json.tmp"))
    }

    def "compareAndSave rejects a stale-version save by the transition service"() {
        given:
        def tenantGuard = new TenantGuard(TenantProperties.DEFAULT)
        def taskStore = new JsonFileTaskStore(storageDir, tenantGuard)
        def boards = new BoardFileLoader(new DefaultResourceLoader())
                .loadAll(["classpath:boards/e2e-content-review.yaml"])
        def boardService = new KanbanBoardService(tenantGuard)
        boardService.registerAll(boards)
        def transitionService = new TaskTransitionService(
                taskStore, boardService, new TransitionGraphStateEngine(),
                new TransitionHistory(50),
                { Object _ignored -> } as ApplicationEventPublisher,
                new KanbanHookFirer(null), tenantGuard)
        def card = transitionService.createCard("e2e-content-review", "X", "Y", [:])
        transitionService.transition(card.id(), "START", null)
        def stale = taskStore.findById(card.id()).get().withVersion(0L) // pretend the caller is behind

        when: "a stale-version save attempts to advance via the SPI directly"
        def result = taskStore.compareAndSave(stale)

        then:
        result.isEmpty()
    }

    def "validator rejects a broken twin of the fixture board"() {
        given:
        def yaml = """
            id: broken
            initialState: backlog
            columns:
              - { state: backlog, phase: QUEUED }
              - { state: review,  phase: RUNNING }
              - { state: done,    phase: SUCCEEDED, terminal: true, terminalKind: SUCCESS }
            transitions:
              - { from: backlog, to: drafttng, event: START }   # typo: drafttng
              - { from: review,  to: done,     event: APPROVE }
        """.stripIndent()
        def file = storageDir.resolve("broken.yaml")
        Files.writeString(file, yaml)
        def loader = new BoardFileLoader(new DefaultResourceLoader())
        def boards = loader.loadAll(["file:${file.toAbsolutePath()}" as String])

        when:
        def report = new BoardValidator().validate(boards)

        then:
        report.hasErrors()
        report.errors().any { it.code() == "UNKNOWN_TO_STATE" }
    }
}

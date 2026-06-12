package io.jaiclaw.kanban.recovery

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.KanbanProperties
import io.jaiclaw.kanban.events.KanbanHookFirer
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
import io.jaiclaw.tasks.persistence.TenantRoutingTaskStore
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Plan §9 group 4 / Definition-of-Done:
 * "Update KanbanRecoveryManager to iterate (tenantId, store) pairs with
 *  tenant context per pass". This spec proves the sweep visits every
 * tenant's backend, not just the default — which is what would happen
 * if the iteration ran under the boot-time (no-tenant) context against
 * a TenantRoutingTaskStore.
 *
 * <p>Lighter than a full Spring Boot context: wires the manager + a
 * routing store + per-tenant Json stores directly, then asserts a
 * RUNNING card stranded on a processor column in each tenant is moved
 * to {@code blocked} by the FAIL policy.
 */
class KanbanRecoveryRoutingSpec extends Specification {

    @TempDir
    Path tempDir

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))
    TaskStore defaultStore
    TaskStore alphaStore
    TaskStore betaStore
    TenantRoutingTaskStore router
    KanbanBoardService boardService
    TaskTransitionService transitionService
    KanbanRecoveryManager manager
    List<Object> published = []
    ApplicationEventPublisher publisher = { Object event -> published.add(event) } as ApplicationEventPublisher

    def setup() {
        defaultStore = new JsonFileTaskStore(Files.createTempDirectory(tempDir, "def"))
        alphaStore   = new JsonFileTaskStore(Files.createTempDirectory(tempDir, "a"), multiGuard)
        betaStore    = new JsonFileTaskStore(Files.createTempDirectory(tempDir, "b"), multiGuard)
        router = new TenantRoutingTaskStore(defaultStore, multiGuard)
        router.register("alpha", alphaStore)
        router.register("beta",  betaStore)

        boardService = new KanbanBoardService(multiGuard)
        boardService.cache(board())

        def engine = new TransitionGraphStateEngine()
        transitionService = new TaskTransitionService(
                router, boardService, engine, new TransitionHistory(50),
                publisher, new KanbanHookFirer(null), multiGuard)

        def props = new KanbanProperties(true, tempDir.toString(),
                null, null, null, null, null,
                new KanbanProperties.Recovery(true, "fail", 3, "30m"),
                null, null)
        manager = new KanbanRecoveryManager(boardService, router,
                transitionService, props, publisher)
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    private BoardDefinition board() {
        def processor = new ProcessorDefinition("agent",
                "Draft {{name}}", "SUBMIT", "BLOCK",
                true, "fail", 3, [:])
        new BoardDefinition("rb", "Routing Board", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog",  TaskStatus.QUEUED,    null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING,   null, false, null, processor),
                new ColumnDefinition("review",   "Review",   TaskStatus.RUNNING,   null, false, null, null),
                new ColumnDefinition("blocked",  "Blocked",  TaskStatus.BLOCKED,   null, false, null, null),
                new ColumnDefinition("done",     "Done",     TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START",   [:]),
                new TransitionDefinition("drafting", "review",   "SUBMIT",  [:]),
                new TransitionDefinition("drafting", "blocked",  "BLOCK",   [:]),
        ])
    }

    private void seedStuckCard(String tenantId, String id) {
        TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId))
        def card = transitionService.createCard("rb", "Stuck-${id}", null, [:])
        transitionService.transition(card.id(), "START", "user")
        // Force RUNNING + drafting so the sweep finds the card.
        def cur = router.findById(card.id()).get()
        router.save(new TaskRecord(cur.id(), cur.name(), cur.description(),
                TaskStatus.RUNNING, cur.deliveryState(), cur.result(), cur.error(),
                cur.flowId(), cur.metadata(),
                cur.createdAt(), cur.startedAt(), cur.completedAt(), cur.tenantId(),
                cur.boardId(), "drafting", cur.assignee(),
                cur.version(), cur.orderIndex(), cur.idempotencyKey()))
    }

    def "sweep visits every tenant's backend, not just the default"() {
        given:
        seedStuckCard("alpha", "a")
        seedStuckCard("beta",  "b")
        TenantContextHolder.clear()

        when:
        int examined = manager.sweepStartup()

        then: "both stuck cards were found across alpha + beta backends"
        examined == 2

        when: "verify each tenant's card landed on blocked under its own context"
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        def alphaCard = router.findAll().find { it.name() == "Stuck-a" }
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        def betaCard = router.findAll().find { it.name() == "Stuck-b" }

        then:
        alphaCard.state() == "blocked"
        betaCard.state() == "blocked"
    }
}

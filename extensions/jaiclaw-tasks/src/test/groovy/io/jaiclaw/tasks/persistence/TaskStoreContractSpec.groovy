package io.jaiclaw.tasks.persistence

import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import spock.lang.Specification

import java.time.Instant

/**
 * Abstract contract spec every {@link TaskStore} backend must pass. Plan
 * §9 group 4 / Definition of Done bullet 2: "Shared TaskStoreContractSpec
 * passes against JSON, Redis (Testcontainers), and JDBC (H2 +
 * Postgres-via-Testcontainers) providers".
 *
 * <p>Phase 4a ships this against {@code JsonFileTaskStore} and
 * {@code H2TaskStore}. Phase 4b (deferred) will add Redis +
 * Postgres-via-Testcontainers; those concrete subclasses extend this
 * spec and provide their own {@link #createStore} factory — no further
 * coverage rewriting needed.
 *
 * <p>Subclasses MUST:
 * <ul>
 *   <li>Implement {@link #createStore()} to return a clean store each
 *       method invocation</li>
 *   <li>Be tenant-scoped to whatever the default tenant is (SINGLE mode
 *       semantics)</li>
 * </ul>
 *
 * <p>The contract pins these invariants:
 * <ol>
 *   <li>{@code save → findById} round-trips every field, including the
 *       Phase-1 kanban additions</li>
 *   <li>{@code compareAndSave} on a new id (no prior row) accepts and
 *       writes {@code version+1}</li>
 *   <li>{@code compareAndSave} with expected==stored bumps version</li>
 *   <li>{@code compareAndSave} with expected!=stored returns
 *       {@link Optional#empty}</li>
 *   <li>{@code findByStatus} filters correctly</li>
 *   <li>{@code findByBoardAndState} filters by board AND state</li>
 *   <li>{@code deleteById} actually removes the row</li>
 *   <li>{@code count} matches the number of records visible to the
 *       current tenant</li>
 * </ol>
 */
abstract class TaskStoreContractSpec extends Specification {

    /**
     * Build a fresh, empty store for each test method. SINGLE-tenant mode
     * is assumed; multi-tenant routing is exercised separately by the
     * {@code TenantRoutingTaskStoreSpec}.
     */
    abstract TaskStore createStore()

    TaskStore store

    def setup() {
        store = createStore()
    }

    /** Tenant id of the store created by {@link #createStore} — override if needed. */
    protected String defaultTenantId() { return "default" }

    private TaskRecord task(String id, TaskStatus status = TaskStatus.QUEUED,
                             long version = 0L) {
        new TaskRecord(id, "T-${id}", "desc",
                status, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null,
                defaultTenantId(),
                null, null, null, version, 0, null)
    }

    private TaskRecord card(String id, String boardId, String state) {
        new TaskRecord(id, "T-${id}", null,
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null,
                defaultTenantId(),
                boardId, state, null, 0L, 0, null)
    }

    def "save then findById round-trips every field"() {
        given:
        def t = new TaskRecord("t1", "Round Trip", "desc",
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                "result", null, "flow-1",
                [k1: "v1", k2: "v2"],
                Instant.parse("2026-06-12T00:00:00Z"),
                Instant.parse("2026-06-12T00:01:00Z"),
                null, defaultTenantId(),
                "b1", "drafting", "alice", 3L, 7, "key-X")

        when:
        store.save(t)
        def loaded = store.findById("t1").orElse(null)

        then:
        loaded != null
        loaded.id() == "t1"
        loaded.name() == "Round Trip"
        loaded.description() == "desc"
        loaded.status() == TaskStatus.RUNNING
        loaded.deliveryState() == TaskDeliveryState.PENDING
        loaded.result() == "result"
        loaded.flowId() == "flow-1"
        loaded.metadata() == [k1: "v1", k2: "v2"]
        loaded.boardId() == "b1"
        loaded.state() == "drafting"
        loaded.assignee() == "alice"
        loaded.version() == 3L
        loaded.orderIndex() == 7
        loaded.idempotencyKey() == "key-X"
    }

    def "findById of an unknown id returns empty"() {
        expect:
        store.findById("missing").isEmpty()
    }

    def "compareAndSave on a new id accepts version=0 and writes version=1"() {
        when:
        def result = store.compareAndSave(task("c-new", TaskStatus.QUEUED, 0L))

        then:
        result.isPresent()
        result.get().version() == 1L

        and: "the persisted row carries version=1"
        store.findById("c-new").get().version() == 1L
    }

    def "compareAndSave with stored==expected bumps version"() {
        given:
        store.save(task("c-bump").withVersion(0L))
        def stored = store.findById("c-bump").get()

        when:
        def result = store.compareAndSave(stored.withState("drafting"))

        then:
        result.isPresent()
        result.get().version() == 1L
    }

    def "compareAndSave rejects stale writes"() {
        given:
        store.save(task("c-stale").withVersion(1L))     // stored at v=1
        def stale = task("c-stale").withVersion(0L)     // writer holds v=0

        when:
        def result = store.compareAndSave(stale)

        then:
        result.isEmpty()
        store.findById("c-stale").get().version() == 1L
    }

    def "findByStatus filters by status"() {
        given:
        store.save(task("q1", TaskStatus.QUEUED))
        store.save(task("q2", TaskStatus.QUEUED))
        store.save(task("r1", TaskStatus.RUNNING))

        expect:
        store.findByStatus(TaskStatus.QUEUED)*.id() as Set == ["q1", "q2"] as Set
        store.findByStatus(TaskStatus.RUNNING)*.id() == ["r1"]
        store.findByStatus(TaskStatus.SUCCEEDED).isEmpty()
    }

    def "findByBoardAndState returns the right (board, state) intersection"() {
        given:
        store.save(card("a", "b1", "drafting"))
        store.save(card("b", "b1", "drafting"))
        store.save(card("c", "b1", "review"))
        store.save(card("d", "b2", "drafting"))

        expect:
        store.findByBoardAndState("b1", "drafting")*.id() as Set == ["a", "b"] as Set
        store.findByBoardAndState("b1", "review")*.id() == ["c"]
        store.findByBoardAndState("b2", "drafting")*.id() == ["d"]
        store.findByBoardAndState("ghost", "drafting").isEmpty()
    }

    def "deleteById removes the row and the count reflects it"() {
        given:
        store.save(task("d1"))
        store.save(task("d2"))

        when:
        store.deleteById("d1")

        then:
        store.findById("d1").isEmpty()
        store.findById("d2").isPresent()
        store.count() == 1L
    }

    def "count starts at 0 and grows by 1 per saved row"() {
        expect:
        store.count() == 0L

        when:
        store.save(task("c1"))
        store.save(task("c2"))

        then:
        store.count() == 2L
    }

    def "findAll returns every saved record visible to the current tenant"() {
        given:
        store.save(task("a"))
        store.save(task("b"))
        store.save(task("c"))

        expect:
        store.findAll()*.id() as Set == ["a", "b", "c"] as Set
    }
}

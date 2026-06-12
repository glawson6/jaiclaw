package io.jaiclaw.tasks.persistence.h2

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import org.h2.Driver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

class H2TaskStoreSpec extends Specification {

    static final AtomicInteger DB_SEQ = new AtomicInteger(0)
    JdbcTemplate jdbc
    H2TaskStore store

    def setup() {
        // Isolated in-memory DB per spec method.
        def url = "jdbc:h2:mem:taskstore-${DB_SEQ.incrementAndGet()};DB_CLOSE_DELAY=-1"
        def ds = new SimpleDriverDataSource(new Driver(), url, "sa", "")
        jdbc = new JdbcTemplate(ds)
        // Apply the same schema the autoconfig relies on Spring Boot to load.
        def sql = getClass().getResourceAsStream("/schema.sql").getText(StandardCharsets.UTF_8.name())
        jdbc.execute(sql)
        store = new H2TaskStore(jdbc)
    }

    private TaskRecord task(String id, long version = 0L) {
        new TaskRecord(id, "T-${id}", "desc",
                TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null, null,
                null, null, null, version, 0, null)
    }

    private TaskRecord card(String id, String boardId, String state, long version = 0L) {
        new TaskRecord(id, "T-${id}", null,
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null, null,
                boardId, state, null, version, 0, null)
    }

    def "save then findById round-trips every field"() {
        given:
        def t = new TaskRecord("t1", "Round Trip", "desc",
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                "result", null, "flow-1",
                [k1: "v1", k2: "v2"],
                Instant.parse("2026-06-12T00:00:00Z"),
                Instant.parse("2026-06-12T00:01:00Z"),
                null, "default",
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

    def "compareAndSave inserts a new row at version=1 and rejects stale writes"() {
        given: "a brand-new record at version=0"
        def t = task("c1", 0L)

        when: "first writer goes through"
        def first = store.compareAndSave(t)

        then:
        first.isPresent()
        first.get().version() == 1L

        when: "second writer still holds the old version=0 record"
        def second = store.compareAndSave(t)

        then:
        second.isEmpty()

        and: "the on-disk row stayed at version 1"
        store.findById("c1").get().version() == 1L
    }

    def "compareAndSave succeeds when expected version matches stored version"() {
        given:
        def t = task("c2", 0L)
        store.compareAndSave(t)
        def stored = store.findById("c2").get()

        when:
        def again = store.compareAndSave(stored.withState("drafting").withStatus(TaskStatus.RUNNING))

        then:
        again.isPresent()
        again.get().version() == 2L
        store.findById("c2").get().state() == "drafting"
    }

    def "findByStatus filters to the right status and tenant"() {
        given:
        store.save(task("q1").withStatus(TaskStatus.QUEUED))
        store.save(task("r1").withStatus(TaskStatus.RUNNING))
        store.save(task("q2").withStatus(TaskStatus.QUEUED))

        expect:
        store.findByStatus(TaskStatus.QUEUED)*.id() as Set == ["q1", "q2"] as Set
        store.findByStatus(TaskStatus.RUNNING)*.id() as Set == ["r1"] as Set
    }

    def "findByBoardAndState returns ordered cards"() {
        given:
        store.save(card("a", "b1", "drafting"))
        store.save(card("b", "b1", "drafting"))
        store.save(card("c", "b1", "review"))
        store.save(card("d", "b2", "drafting"))

        expect:
        store.findByBoardAndState("b1", "drafting")*.id() as Set == ["a", "b"] as Set
        store.findByBoardAndState("b1", "review")*.id() == ["c"]
        store.findByBoardAndState("b2", "drafting")*.id() == ["d"]
    }

    def "deleteById removes the row"() {
        given:
        store.save(task("d1"))

        when:
        store.deleteById("d1")

        then:
        store.findById("d1").isEmpty()
        store.count() == 0L
    }

    def "MULTI tenant mode isolates rows per tenant"() {
        given:
        def multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))
        def multiStore = new H2TaskStore(jdbc, multiGuard)
        def alphaTask = new TaskRecord("shared", "A", null, TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.parse("2026-06-12T00:00:00Z"), null, null,
                "alpha", null, null, null, 0L, 0, null)
        def betaTask = new TaskRecord("shared", "B", null, TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.parse("2026-06-12T00:00:00Z"), null, null,
                "beta", null, null, null, 0L, 0, null)

        when:
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        multiStore.save(alphaTask)
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        multiStore.save(betaTask)

        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        def alphaLoaded = multiStore.findById("shared")
        def alphaCount = multiStore.count()
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        def betaLoaded = multiStore.findById("shared")
        def betaCount = multiStore.count()

        then:
        alphaLoaded.get().name() == "A"
        alphaCount == 1L
        betaLoaded.get().name() == "B"
        betaCount == 1L

        cleanup:
        TenantContextHolder.clear()
    }

    def "lease: claim succeeds when nobody holds it"() {
        given:
        store.save(task("l1"))

        when:
        def claimed = store.claim("l1", "instance-A",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        then:
        claimed
    }

    def "lease: second instance can claim an expired lease"() {
        given:
        store.save(task("l2"))
        store.claim("l2", "instance-A",
                Instant.now().minus(10, ChronoUnit.SECONDS))   // already expired

        when:
        def claimed = store.claim("l2", "instance-B",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        then:
        claimed
    }

    def "lease: second instance cannot claim a live lease"() {
        given:
        store.save(task("l3"))
        store.claim("l3", "instance-A",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        when:
        def claimed = store.claim("l3", "instance-B",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        then:
        !claimed
    }

    def "lease: same instance can re-claim its own live lease"() {
        given:
        store.save(task("l4"))
        store.claim("l4", "instance-A",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        when:
        def again = store.claim("l4", "instance-A",
                Instant.now().plus(120, ChronoUnit.SECONDS))

        then:
        again
    }

    def "lease: renew and release"() {
        given:
        store.save(task("l5"))
        store.claim("l5", "instance-A",
                Instant.now().plus(60, ChronoUnit.SECONDS))

        when:
        def renewed = store.renewLease("l5", "instance-A",
                Instant.now().plus(120, ChronoUnit.SECONDS))

        then:
        renewed

        when:
        def released = store.releaseLease("l5", "instance-A")

        then:
        released

        and: "a different instance can claim once it's free"
        store.claim("l5", "instance-B",
                Instant.now().plus(60, ChronoUnit.SECONDS))
    }

    def "findExpiredLeases lists rows whose lease passed"() {
        given:
        store.save(task("e1"))
        store.save(task("e2"))
        store.save(task("e3"))
        // e1 and e3 have expired leases, e2 is still live.
        store.claim("e1", "X", Instant.now().minus(5, ChronoUnit.SECONDS))
        store.claim("e2", "X", Instant.now().plus(60, ChronoUnit.SECONDS))
        store.claim("e3", "Y", Instant.now().minus(10, ChronoUnit.SECONDS))

        expect:
        store.findExpiredLeases(Instant.now()) as Set == ["e1", "e3"] as Set
    }
}

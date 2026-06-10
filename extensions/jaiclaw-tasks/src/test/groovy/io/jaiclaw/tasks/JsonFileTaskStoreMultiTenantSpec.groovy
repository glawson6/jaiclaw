package io.jaiclaw.tasks

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

/**
 * Verifies the audit fix for {@code JsonFileTaskStore}: previously every
 * call collapsed onto {@code tasks.id()} as the key, so two tenants
 * using the same business-domain task id silently overwrote each other.
 * After the fix, keys are tenant-scoped via
 * {@link TenantGuard#resolveStorageKey(String)} and reads filter by the
 * current tenant prefix.
 */
class JsonFileTaskStoreMultiTenantSpec extends Specification {

    @TempDir
    Path tempDir

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))

    def setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    private TaskRecord makeTask(String id, String tenantId, String name) {
        new TaskRecord(
                id, name, "desc", TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, Map.of(), Instant.now(), null, null, tenantId)
    }

    def "save as tenant A and tenant B with same id does not collide"() {
        given:
        def store = new JsonFileTaskStore(tempDir, multiGuard)

        when:
        setTenant("tenant-a")
        store.save(makeTask("task-1", "tenant-a", "A's task"))

        setTenant("tenant-b")
        store.save(makeTask("task-1", "tenant-b", "B's task"))

        and:
        setTenant("tenant-a")
        def a = store.findById("task-1")

        and:
        setTenant("tenant-b")
        def b = store.findById("task-1")

        then:
        a.get().name() == "A's task"
        b.get().name() == "B's task"
    }

    def "findAll only returns the current tenant's records"() {
        given:
        def store = new JsonFileTaskStore(tempDir, multiGuard)

        when:
        setTenant("tenant-a")
        store.save(makeTask("a-1", "tenant-a", "A-1"))
        store.save(makeTask("a-2", "tenant-a", "A-2"))

        setTenant("tenant-b")
        store.save(makeTask("b-1", "tenant-b", "B-1"))

        and:
        setTenant("tenant-a")
        def aIds = store.findAll()*.id() as Set
        def aCount = store.count()

        and:
        setTenant("tenant-b")
        def bIds = store.findAll()*.id() as Set
        def bCount = store.count()

        then:
        aIds == ["a-1", "a-2"] as Set
        aCount == 2L
        bIds == ["b-1"] as Set
        bCount == 1L
    }

    def "deleteById removes only from the current tenant's namespace"() {
        given:
        def store = new JsonFileTaskStore(tempDir, multiGuard)

        when:
        setTenant("tenant-a")
        store.save(makeTask("shared-id", "tenant-a", "A version"))

        setTenant("tenant-b")
        store.save(makeTask("shared-id", "tenant-b", "B version"))
        store.deleteById("shared-id")
        def bAfter = store.findById("shared-id")

        and:
        setTenant("tenant-a")
        def aAfter = store.findById("shared-id")

        then:
        bAfter.isEmpty()
        aAfter.get().name() == "A version"
    }

    def "records survive restart with their tenant prefixes intact"() {
        given:
        def store = new JsonFileTaskStore(tempDir, multiGuard)

        and:
        setTenant("tenant-a")
        store.save(makeTask("task-x", "tenant-a", "A's"))
        setTenant("tenant-b")
        store.save(makeTask("task-x", "tenant-b", "B's"))

        when: "fresh store instance reads the same file"
        TenantContextHolder.clear()
        def store2 = new JsonFileTaskStore(tempDir, multiGuard)

        and:
        setTenant("tenant-a")
        def a = store2.findById("task-x")

        and:
        setTenant("tenant-b")
        def b = store2.findById("task-x")

        then:
        a.get().name() == "A's"
        b.get().name() == "B's"
    }
}

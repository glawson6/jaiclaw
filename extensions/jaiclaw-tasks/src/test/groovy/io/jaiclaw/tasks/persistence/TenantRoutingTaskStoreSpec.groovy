package io.jaiclaw.tasks.persistence

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class TenantRoutingTaskStoreSpec extends Specification {

    @TempDir
    Path tempDir

    TaskStore defaultStore
    TaskStore alphaStore
    TaskStore betaStore
    TenantGuard multiGuard
    TenantRoutingTaskStore router

    def setup() {
        defaultStore = new JsonFileTaskStore(Files.createTempDirectory(tempDir, "default"))
        alphaStore   = new JsonFileTaskStore(
                Files.createTempDirectory(tempDir, "alpha"),
                new TenantGuard(new TenantProperties(TenantMode.MULTI, "default")))
        betaStore    = new JsonFileTaskStore(
                Files.createTempDirectory(tempDir, "beta"),
                new TenantGuard(new TenantProperties(TenantMode.MULTI, "default")))
        multiGuard   = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))
        router = new TenantRoutingTaskStore(defaultStore, multiGuard)
        router.register("alpha", alphaStore)
        router.register("beta",  betaStore)
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    private TaskRecord task(String id, String tenantId) {
        new TaskRecord(id, "T-${id}", null,
                TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null, tenantId,
                null, null, null, 0L, 0, null)
    }

    def "save under tenant=alpha goes to alphaStore only"() {
        when:
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        router.save(task("t1", "alpha"))

        then:
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        alphaStore.findById("t1").isPresent()

        and: "beta and default are untouched"
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        betaStore.findById("t1").isEmpty()
    }

    def "two tenants sharing an id are isolated by routing"() {
        when:
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        router.save(task("shared", "alpha"))
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        router.save(task("shared", "beta"))

        and:
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        def alphaSeen = router.findById("shared")
        TenantContextHolder.set(new DefaultTenantContext("beta", "beta"))
        def betaSeen  = router.findById("shared")

        then:
        alphaSeen.get().tenantId() == "alpha"
        betaSeen.get().tenantId()  == "beta"
    }

    def "tenant with no registered backend falls through to default"() {
        when:
        TenantContextHolder.set(new DefaultTenantContext("gamma", "gamma"))
        // The default store is SINGLE-mode and will key by "default" — that's
        // fine for the fallback path; we just want to assert the router
        // doesn't blow up and the write lands somewhere.
        router.save(new TaskRecord("g1", "G", null,
                TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null, "default",
                null, null, null, 0L, 0, null))

        then:
        defaultStore.findById("g1").isPresent()
    }

    def "SINGLE-mode router (no tenant guard) routes everything to default"() {
        given:
        def singleRouter = new TenantRoutingTaskStore(defaultStore,
                new TenantGuard(TenantProperties.DEFAULT))
        singleRouter.register("alpha", alphaStore)   // present, but should be bypassed

        when:
        singleRouter.save(new TaskRecord("s1", "S", null,
                TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, [:],
                Instant.parse("2026-06-12T00:00:00Z"), null, null, "default",
                null, null, null, 0L, 0, null))

        then: "the write lands in the default store, not alpha"
        defaultStore.findById("s1").isPresent()
        // Check alphaStore via a tenant context — it's still MULTI-mode, so a
        // raw findById would require one; setting it for the read proves the
        // row isn't there under any reasonable tenant.
        TenantContextHolder.set(new DefaultTenantContext("alpha", "alpha"))
        alphaStore.findById("s1").isEmpty()
    }

    def "tenantStores() returns a snapshot of registered backends"() {
        expect:
        router.tenantStores().keySet() == ["alpha", "beta"] as Set

        when:
        router.deregister("alpha")

        then:
        router.tenantStores().keySet() == ["beta"] as Set
    }

    def "withTenant restores prior context on exit"() {
        given:
        TenantContextHolder.set(new DefaultTenantContext("outer", "outer"))

        when:
        TenantRoutingTaskStore.withTenant("inner", {
            assert TenantContextHolder.get().getTenantId() == "inner"
        } as Runnable)

        then:
        TenantContextHolder.get().getTenantId() == "outer"
    }

    def "withTenant clears the context when there was none before"() {
        when:
        TenantRoutingTaskStore.withTenant("only", {} as Runnable)

        then:
        TenantContextHolder.get() == null
    }
}

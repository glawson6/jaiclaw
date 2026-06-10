package io.jaiclaw.voicecall.store

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.voicecall.model.*
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class JsonlCallStoreMultiTenantSpec extends Specification {

    @TempDir
    Path tempDir
    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false))

    def cleanup() { TenantContextHolder.clear() }

    private void setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    private CallRecord call(String id, String tenantId) {
        CallRecord r = new CallRecord(id, "twilio", CallDirection.OUTBOUND,
                "+15550000000", "+15551111111", CallMode.CONVERSATION)
        r.tenantId = tenantId
        return r
    }

    def "each tenant's records land in its own subdirectory"() {
        given:
        def store = new JsonlCallStore(tempDir, multiGuard)

        when:
        setTenant("tenant-a")
        store.persist(call("c1", "tenant-a"))
        setTenant("tenant-b")
        store.persist(call("c1", "tenant-b"))
        store.shutdown()
        Thread.sleep(300)

        then:
        Files.exists(tempDir.resolve("tenant-a").resolve("calls.jsonl"))
        Files.exists(tempDir.resolve("tenant-b").resolve("calls.jsonl"))
    }

    def "getHistory is filtered by current tenant context"() {
        given:
        def store = new JsonlCallStore(tempDir, multiGuard)
        setTenant("tenant-a")
        store.persist(call("c1", "tenant-a"))
        setTenant("tenant-b")
        store.persist(call("c2", "tenant-b"))
        Thread.sleep(200)

        when:
        setTenant("tenant-a")
        List<CallRecord> aHistory = store.getHistory(10)
        setTenant("tenant-b")
        List<CallRecord> bHistory = store.getHistory(10)

        then:
        aHistory*.callId == ["c1"]
        bHistory*.callId == ["c2"]

        cleanup:
        store.shutdown()
    }

    def "writer thread sees the originating tenant context"() {
        given:
        def store = new JsonlCallStore(tempDir, multiGuard)

        when:
        // Persist under tenant-a, then immediately move on.
        setTenant("tenant-a")
        CallRecord r = call("c1", "tenant-a")
        store.persist(r)
        TenantContextHolder.clear()
        Thread.sleep(200)

        then:
        // File landed under tenant-a/ — i.e. the writer thread had the right context.
        Files.exists(tempDir.resolve("tenant-a").resolve("calls.jsonl"))

        cleanup:
        store.shutdown()
    }
}

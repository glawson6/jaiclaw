package io.jaiclaw.docstore.repository

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.docstore.model.DocStoreEntry
import spock.lang.Specification

/**
 * Verifies the audit fix for {@code InMemoryDocStoreRepository}: previously
 * {@code entries.put(entry.id(), entry)} clobbered cross-tenant records that
 * shared a business-domain id. After the fix, keys are tenant-scoped and
 * reads filter by current-tenant prefix.
 */
class InMemoryDocStoreRepositoryMultiTenantSpec extends Specification {

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))
    InMemoryDocStoreRepository repo = new InMemoryDocStoreRepository(multiGuard)

    def setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    private DocStoreEntry makeEntry(String id, String tenantId) {
        DocStoreEntry.builder()
                .id(id)
                .entryType(DocStoreEntry.EntryType.FILE)
                .filename("file-${id}.txt")
                .tenantId(tenantId)
                .build()
    }

    def "two tenants saving the same entry id do not collide"() {
        when:
        setTenant("tenant-a")
        repo.save(makeEntry("doc-1", "tenant-a"))

        setTenant("tenant-b")
        repo.save(makeEntry("doc-1", "tenant-b"))

        and:
        setTenant("tenant-a")
        def a = repo.findById("doc-1")

        and:
        setTenant("tenant-b")
        def b = repo.findById("doc-1")

        then:
        a.get().tenantId() == "tenant-a"
        b.get().tenantId() == "tenant-b"
    }

    def "findRecent only surfaces the current tenant's entries"() {
        when:
        setTenant("tenant-a")
        repo.save(makeEntry("a-1", "tenant-a"))
        repo.save(makeEntry("a-2", "tenant-a"))

        setTenant("tenant-b")
        repo.save(makeEntry("b-1", "tenant-b"))

        and:
        setTenant("tenant-a")
        def aIds = repo.findRecent(null, 10)*.id() as Set
        def aCount = repo.count(null)

        and:
        setTenant("tenant-b")
        def bIds = repo.findRecent(null, 10)*.id() as Set
        def bCount = repo.count(null)

        then:
        aIds == ["a-1", "a-2"] as Set
        aCount == 2L
        bIds == ["b-1"] as Set
        bCount == 1L
    }

    def "deleteById does not delete the other tenant's record"() {
        given:
        setTenant("tenant-a")
        repo.save(makeEntry("shared-id", "tenant-a"))
        setTenant("tenant-b")
        repo.save(makeEntry("shared-id", "tenant-b"))

        when:
        setTenant("tenant-b")
        repo.deleteById("shared-id")
        def bAfterDelete = repo.findById("shared-id")

        and:
        setTenant("tenant-a")
        def aAfterDelete = repo.findById("shared-id")

        then:
        bAfterDelete.isEmpty()
        aAfterDelete.get().tenantId() == "tenant-a"
    }
}

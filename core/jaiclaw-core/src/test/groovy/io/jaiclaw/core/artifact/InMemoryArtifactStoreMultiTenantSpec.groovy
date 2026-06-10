package io.jaiclaw.core.artifact

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification

import java.time.Instant

class InMemoryArtifactStoreMultiTenantSpec extends Specification {

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false))
    InMemoryArtifactStore store = new InMemoryArtifactStore(multiGuard)

    def cleanup() {
        TenantContextHolder.clear()
    }

    private void setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    private StoredArtifact artifact(String id) {
        return new StoredArtifact(id, new byte[]{0x01}, "application/octet-stream", id + ".bin",
                ArtifactStatus.COMPLETED, "ok", Instant.now(), [:])
    }

    def "tenant B cannot read tenant A's artifact with the same business id"() {
        given:
        setTenant("tenant-a")
        store.save(artifact("doc-1"))

        when:
        setTenant("tenant-b")
        Optional<StoredArtifact> got = store.findById("doc-1")

        then:
        got.isEmpty()
    }

    def "two tenants can store distinct copies under the same id without colliding"() {
        given:
        setTenant("tenant-a")
        store.save(artifact("doc-1"))
        setTenant("tenant-b")
        store.save(artifact("doc-1"))

        when:
        setTenant("tenant-a")
        Optional<StoredArtifact> a = store.findById("doc-1")
        setTenant("tenant-b")
        Optional<StoredArtifact> b = store.findById("doc-1")

        then:
        a.isPresent()
        b.isPresent()
        // Both tenants see *their own* artifact.
        a.get().id() == "doc-1"
        b.get().id() == "doc-1"
    }

    def "tenant B's delete does not affect tenant A's data"() {
        given:
        setTenant("tenant-a")
        store.save(artifact("doc-1"))

        when:
        setTenant("tenant-b")
        store.delete("doc-1")
        setTenant("tenant-a")
        Optional<StoredArtifact> survivor = store.findById("doc-1")

        then:
        survivor.isPresent()
    }

    def "findAllForCurrentTenant only sees the current tenant's artifacts"() {
        given:
        setTenant("tenant-a")
        store.save(artifact("doc-a-1"))
        store.save(artifact("doc-a-2"))
        setTenant("tenant-b")
        store.save(artifact("doc-b-1"))

        when:
        setTenant("tenant-a")
        List<StoredArtifact> seenByA = store.findAllForCurrentTenant().toList()
        setTenant("tenant-b")
        List<StoredArtifact> seenByB = store.findAllForCurrentTenant().toList()

        then:
        seenByA*.id().toSorted() == ["doc-a-1", "doc-a-2"]
        seenByB*.id() == ["doc-b-1"]
    }

    def "updateStatus only mutates the current tenant's artifact"() {
        given:
        setTenant("tenant-a")
        store.save(artifact("doc-1"))
        setTenant("tenant-b")
        store.save(artifact("doc-1"))

        when:
        setTenant("tenant-a")
        store.updateStatus("doc-1", ArtifactStatus.FAILED, "tenant-a's failure")
        setTenant("tenant-b")
        Optional<StoredArtifact> bAfter = store.findById("doc-1")

        then:
        bAfter.isPresent()
        bAfter.get().status() == ArtifactStatus.COMPLETED
        bAfter.get().statusMessage() == "ok"
    }
}

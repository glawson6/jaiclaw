package io.jaiclaw.agentmind.memory.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Plan §6 task 2.15 — dedicated isolation spec. Verifies the store layer
 * blocks every cross-tenant + cross-agent + cross-peer leak vector across
 * all three Memory scopes. Mirrors the soul module's HermesStoreIsolationSpec
 * shape; Phase 3 (Tendencies) will extend with its own scope dimensions.
 */
class MemoryThreeScopeIsolationSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard multiTenant = Mock() { isMultiTenant() >> true }
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())

    BoundedBlobMemoryStore provider = new BoundedBlobMemoryStore(tmp, multiTenant, mapper)

    // ---------- cross-tenant isolation (all scopes) ----------

    def "TENANT-scope reads do not leak across tenants"() {
        given:
        provider.saveMemory(MemoryDocument.forTenant("tenantA", "A org voice", 4096))
        provider.saveMemory(MemoryDocument.forTenant("tenantB", "B org voice", 4096))

        expect:
        provider.findMemory("tenantA", MemoryScope.TENANT, null, null).get().content() == "A org voice"
        provider.findMemory("tenantB", MemoryScope.TENANT, null, null).get().content() == "B org voice"
    }

    def "AGENT-scope reads do not leak across tenants"() {
        given:
        provider.saveMemory(MemoryDocument.forAgent("tenantA", "shared-id", "A notes", 2200))
        provider.saveMemory(MemoryDocument.forAgent("tenantB", "shared-id", "B notes", 2200))

        expect:
        provider.findMemory("tenantA", MemoryScope.AGENT, "shared-id", null).get().content() == "A notes"
        provider.findMemory("tenantB", MemoryScope.AGENT, "shared-id", null).get().content() == "B notes"
    }

    def "PEER-scope reads do not leak across tenants"() {
        given:
        provider.saveMemory(MemoryDocument.forPeer("tenantA", "bot", "U99", "A peer", 1375))
        provider.saveMemory(MemoryDocument.forPeer("tenantB", "bot", "U99", "B peer", 1375))

        expect:
        provider.findMemory("tenantA", MemoryScope.PEER, "bot", "U99").get().content() == "A peer"
        provider.findMemory("tenantB", MemoryScope.PEER, "bot", "U99").get().content() == "B peer"
    }

    // ---------- within-tenant isolation ----------

    def "AGENT-scope reads do not leak across agents within a tenant"() {
        given:
        provider.saveMemory(MemoryDocument.forAgent("acme", "agentA", "A's notes", 2200))
        provider.saveMemory(MemoryDocument.forAgent("acme", "agentB", "B's notes", 2200))

        expect:
        provider.findMemory("acme", MemoryScope.AGENT, "agentA", null).get().content() == "A's notes"
        provider.findMemory("acme", MemoryScope.AGENT, "agentB", null).get().content() == "B's notes"
    }

    def "PEER-scope reads do not leak across peers under the same agent"() {
        given:
        provider.saveMemory(MemoryDocument.forPeer("acme", "bot", "U1", "user 1", 1375))
        provider.saveMemory(MemoryDocument.forPeer("acme", "bot", "U2", "user 2", 1375))

        expect:
        provider.findMemory("acme", MemoryScope.PEER, "bot", "U1").get().content() == "user 1"
        provider.findMemory("acme", MemoryScope.PEER, "bot", "U2").get().content() == "user 2"
    }

    def "PEER-scope reads do not leak across agents for the same peer"() {
        given:
        provider.saveMemory(MemoryDocument.forPeer("acme", "botA", "U99", "botA-U99", 1375))
        provider.saveMemory(MemoryDocument.forPeer("acme", "botB", "U99", "botB-U99", 1375))

        expect:
        provider.findMemory("acme", MemoryScope.PEER, "botA", "U99").get().content() == "botA-U99"
        provider.findMemory("acme", MemoryScope.PEER, "botB", "U99").get().content() == "botB-U99"
    }

    // ---------- cross-scope isolation ----------

    def "deleting TENANT scope leaves AGENT and PEER scopes intact"() {
        given:
        provider.saveMemory(MemoryDocument.forTenant("acme", "tenant body", 4096))
        provider.saveMemory(MemoryDocument.forAgent("acme", "bot", "agent body", 2200))
        provider.saveMemory(MemoryDocument.forPeer("acme", "bot", "U99", "peer body", 1375))

        when:
        provider.deleteMemory("acme", MemoryScope.TENANT, null, null)

        then:
        provider.findMemory("acme", MemoryScope.TENANT, null, null).empty
        provider.findMemory("acme", MemoryScope.AGENT, "bot", null).present
        provider.findMemory("acme", MemoryScope.PEER, "bot", "U99").present
    }

    def "deleting one tenant's data leaves the other untouched"() {
        given:
        provider.saveMemory(MemoryDocument.forTenant("tenantA", "A", 4096))
        provider.saveMemory(MemoryDocument.forTenant("tenantB", "B", 4096))

        when:
        provider.deleteMemory("tenantA", MemoryScope.TENANT, null, null)

        then:
        provider.findMemory("tenantA", MemoryScope.TENANT, null, null).empty
        provider.findMemory("tenantB", MemoryScope.TENANT, null, null).get().content() == "B"
    }

    def "an unknown tenant returns empty for all three scopes"() {
        given:
        provider.saveMemory(MemoryDocument.forTenant("acme", "T", 4096))
        provider.saveMemory(MemoryDocument.forAgent("acme", "bot", "A", 2200))
        provider.saveMemory(MemoryDocument.forPeer("acme", "bot", "U99", "P", 1375))

        expect:
        provider.findMemory("ghost", MemoryScope.TENANT, null, null).empty
        provider.findMemory("ghost", MemoryScope.AGENT, "bot", null).empty
        provider.findMemory("ghost", MemoryScope.PEER, "bot", "U99").empty
    }
}

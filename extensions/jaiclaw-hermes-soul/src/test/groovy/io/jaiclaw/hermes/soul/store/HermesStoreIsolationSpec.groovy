package io.jaiclaw.hermes.soul.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Plan §5 task 1.12 — dedicated isolation spec mirroring the kanban
 * TenantIsolationGuardSpec shape. Verifies the store layer blocks every
 * cross-tenant + cross-agent leak vector. Phase 2 (Memory) and Phase 3
 * (Tendencies) will extend this spec class with the corresponding scope
 * dimensions.
 */
class HermesStoreIsolationSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard multiTenant = Mock() { isMultiTenant() >> true }
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())

    FileSoulProvider provider = new FileSoulProvider(tmp, multiTenant, mapper)

    def "AGENT-scope reads do not leak across tenants"() {
        given:
        provider.saveSoul(Soul.forAgent("tenantA", "shared-agent-id", "A's voice"))
        provider.saveSoul(Soul.forAgent("tenantB", "shared-agent-id", "B's voice"))

        expect:
        provider.findSoul("tenantA", SoulScope.AGENT, "shared-agent-id").get().markdown() == "A's voice"
        provider.findSoul("tenantB", SoulScope.AGENT, "shared-agent-id").get().markdown() == "B's voice"
    }

    def "TENANT-scope reads do not leak across tenants"() {
        given:
        provider.saveSoul(Soul.forTenant("tenantA", "A org voice"))
        provider.saveSoul(Soul.forTenant("tenantB", "B org voice"))

        expect:
        provider.findSoul("tenantA", SoulScope.TENANT, null).get().markdown() == "A org voice"
        provider.findSoul("tenantB", SoulScope.TENANT, null).get().markdown() == "B org voice"
    }

    def "AGENT-scope reads do not leak across agents within a tenant"() {
        given:
        provider.saveSoul(Soul.forAgent("acme", "agentA", "A's voice"))
        provider.saveSoul(Soul.forAgent("acme", "agentB", "B's voice"))

        expect:
        provider.findSoul("acme", SoulScope.AGENT, "agentA").get().markdown() == "A's voice"
        provider.findSoul("acme", SoulScope.AGENT, "agentB").get().markdown() == "B's voice"
    }

    def "deleting tenant-scope leaves agent-scope intact"() {
        given:
        provider.saveSoul(Soul.forTenant("acme", "org voice"))
        provider.saveSoul(Soul.forAgent("acme", "bot", "agent voice"))

        when:
        provider.deleteSoul("acme", SoulScope.TENANT, null)

        then:
        provider.findSoul("acme", SoulScope.TENANT, null).empty
        provider.findSoul("acme", SoulScope.AGENT, "bot").present
    }

    def "deleting one tenant's data leaves the other untouched"() {
        given:
        provider.saveSoul(Soul.forTenant("tenantA", "A"))
        provider.saveSoul(Soul.forTenant("tenantB", "B"))

        when:
        provider.deleteSoul("tenantA", SoulScope.TENANT, null)

        then:
        provider.findSoul("tenantA", SoulScope.TENANT, null).empty
        provider.findSoul("tenantB", SoulScope.TENANT, null).get().markdown() == "B"
    }

    def "an unknown tenant returns empty for both scopes"() {
        given:
        provider.saveSoul(Soul.forTenant("acme", "A"))
        provider.saveSoul(Soul.forAgent("acme", "bot", "B"))

        expect:
        provider.findSoul("ghost", SoulScope.TENANT, null).empty
        provider.findSoul("ghost", SoulScope.AGENT, "bot").empty
    }
}

package io.jaiclaw.agentmind.soul.store

import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class JsonAgentMindStoreProviderSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard singleTenant = Mock() { isMultiTenant() >> false }

    def "exposes JSON as its type identifier"() {
        expect:
        new JsonAgentMindStoreProvider(tmp, singleTenant).type() == "json"
        JsonAgentMindStoreProvider.TYPE == "json"
    }

    def "soulStore returns a FileSoulProvider bound to the root + tenantGuard"() {
        when:
        JsonAgentMindStoreProvider p = new JsonAgentMindStoreProvider(tmp, singleTenant)

        then:
        p.soulStore() instanceof FileSoulProvider
    }

    def "soulStore returns the same instance across calls (no churn)"() {
        given:
        JsonAgentMindStoreProvider p = new JsonAgentMindStoreProvider(tmp, singleTenant)

        expect:
        p.soulStore().is(p.soulStore())
    }
}

package io.jaiclaw.hermes.soul.store

import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class JsonHermesStoreProviderSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard singleTenant = Mock() { isMultiTenant() >> false }

    def "exposes JSON as its type identifier"() {
        expect:
        new JsonHermesStoreProvider(tmp, singleTenant).type() == "json"
        JsonHermesStoreProvider.TYPE == "json"
    }

    def "soulStore returns a FileSoulProvider bound to the root + tenantGuard"() {
        when:
        JsonHermesStoreProvider p = new JsonHermesStoreProvider(tmp, singleTenant)

        then:
        p.soulStore() instanceof FileSoulProvider
    }

    def "soulStore returns the same instance across calls (no churn)"() {
        given:
        JsonHermesStoreProvider p = new JsonHermesStoreProvider(tmp, singleTenant)

        expect:
        p.soulStore().is(p.soulStore())
    }
}

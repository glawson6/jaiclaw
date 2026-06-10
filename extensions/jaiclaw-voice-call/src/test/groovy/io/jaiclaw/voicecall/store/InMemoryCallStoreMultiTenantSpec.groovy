package io.jaiclaw.voicecall.store

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.voicecall.model.*
import spock.lang.Specification

class InMemoryCallStoreMultiTenantSpec extends Specification {

    TenantGuard multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false))
    InMemoryCallStore store = new InMemoryCallStore(multiGuard)

    def cleanup() { TenantContextHolder.clear() }

    private void setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    private CallRecord call(String id) {
        return new CallRecord(id, "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
    }

    def "two tenants writing the same callId do not collide"() {
        given:
        setTenant("tenant-a")
        store.persist(call("c1"))
        setTenant("tenant-b")
        store.persist(call("c1"))

        expect:
        store.size() == 2
    }

    def "loadActiveCalls is filtered to the current tenant"() {
        given:
        setTenant("tenant-a")
        CallRecord aActive = call("c1")
        aActive.state = CallState.ACTIVE
        store.persist(aActive)

        setTenant("tenant-b")
        CallRecord bActive = call("c2")
        bActive.state = CallState.ACTIVE
        store.persist(bActive)

        when:
        setTenant("tenant-a")
        Map<String, CallRecord> aSees = store.loadActiveCalls()
        setTenant("tenant-b")
        Map<String, CallRecord> bSees = store.loadActiveCalls()

        then:
        aSees.keySet() == (["c1"] as Set)
        bSees.keySet() == (["c2"] as Set)
    }

    def "getHistory is filtered to the current tenant"() {
        given:
        setTenant("tenant-a")
        store.persist(call("c1"))
        Thread.sleep(10)
        setTenant("tenant-b")
        store.persist(call("c2"))

        when:
        setTenant("tenant-a")
        List<CallRecord> aHistory = store.getHistory(10)
        setTenant("tenant-b")
        List<CallRecord> bHistory = store.getHistory(10)

        then:
        aHistory*.callId == ["c1"]
        bHistory*.callId == ["c2"]
    }
}

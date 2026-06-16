package io.jaiclaw.agent.session

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification

class SessionManagerMultiTenantSpec extends Specification {

    def multiGuard = new TenantGuard(new TenantProperties(TenantMode.MULTI, "default"))
    def manager = new InMemorySessionManager(multiGuard)

    def setTenant(String id) {
        TenantContextHolder.set(new DefaultTenantContext(id, id))
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    def "tenant A creates session, tenant B get() returns empty"() {
        given:
        setTenant("tenant-a")
        manager.getOrCreate("session-1", "agent-1")

        when:
        setTenant("tenant-b")
        def result = manager.get("session-1")

        then:
        result.isEmpty()
    }

    def "listSessions returns only current tenant's sessions"() {
        given:
        setTenant("tenant-a")
        manager.getOrCreate("a-session-1", "agent-1")
        manager.getOrCreate("a-session-2", "agent-1")

        setTenant("tenant-b")
        manager.getOrCreate("b-session-1", "agent-1")

        when:
        setTenant("tenant-a")
        def sessionsA = manager.listSessions()

        then:
        sessionsA.size() == 2
        sessionsA.every { it.tenantId() == "tenant-a" }

        when:
        setTenant("tenant-b")
        def sessionsB = manager.listSessions()

        then:
        sessionsB.size() == 1
        sessionsB[0].tenantId() == "tenant-b"
    }

    def "listActiveSessions is scoped correctly"() {
        given:
        setTenant("tenant-a")
        manager.getOrCreate("a-1", "agent-1")
        manager.getOrCreate("a-2", "agent-1")
        manager.close("a-2")

        setTenant("tenant-b")
        manager.getOrCreate("b-1", "agent-1")

        when:
        setTenant("tenant-a")
        def active = manager.listActiveSessions()

        then:
        active.size() == 1

        when:
        setTenant("tenant-b")
        def activeB = manager.listActiveSessions()

        then:
        activeB.size() == 1
    }

    def "exists returns false for other tenant's session"() {
        given:
        setTenant("tenant-a")
        manager.getOrCreate("shared-key", "agent-1")

        when:
        setTenant("tenant-b")

        then:
        !manager.exists("shared-key")
    }
}

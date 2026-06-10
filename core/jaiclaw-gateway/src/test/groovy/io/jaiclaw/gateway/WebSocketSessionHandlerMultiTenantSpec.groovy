package io.jaiclaw.gateway

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import org.springframework.web.socket.WebSocketSession
import spock.lang.Specification

import java.security.Principal

class WebSocketSessionHandlerMultiTenantSpec extends Specification {

    GatewayService gateway = Mock()

    private WebSocketSession session(String sessionKey, String tenantPrincipalName, Map<String, Object> attrs = [:]) {
        WebSocketSession s = Mock()
        s.getUri() >> URI.create("/ws/session/" + sessionKey)
        if (tenantPrincipalName != null) {
            Principal p = Mock()
            p.getName() >> tenantPrincipalName
            s.getPrincipal() >> p
        } else {
            s.getPrincipal() >> null
        }
        s.isOpen() >> true
        s.getAttributes() >> attrs
        s.getId() >> sessionKey
        return s
    }

    def "MULTI mode keys map by tenant + sessionKey; same sessionKey across tenants does not collide"() {
        given:
        WebSocketSessionHandler handler = new WebSocketSessionHandler(
                gateway, new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false)))

        when:
        handler.afterConnectionEstablished(session("sess-1", "tenant-a", [:]))
        handler.afterConnectionEstablished(session("sess-1", "tenant-b", [:]))

        then:
        handler.activeSessionCount() == 2
        handler.hasActiveSession("tenant-a", "sess-1")
        handler.hasActiveSession("tenant-b", "sess-1")
        // Untyped hasActiveSession(sessionKey) sees both (legacy callers)
        handler.hasActiveSession("sess-1")
    }

    def "MULTI mode without a principal closes the connection"() {
        given:
        WebSocketSessionHandler handler = new WebSocketSessionHandler(
                gateway, new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false)))
        WebSocketSession s = session("sess-1", null, [:])

        when:
        handler.afterConnectionEstablished(s)

        then:
        1 * s.close(_)
        handler.activeSessionCount() == 0
    }

    def "SINGLE mode without a principal uses the configured defaultTenantId"() {
        given:
        WebSocketSessionHandler handler = new WebSocketSessionHandler(
                gateway, new TenantGuard(new TenantProperties(TenantMode.SINGLE, "test-tenant-id-001", false)))
        Map<String, Object> attrs = [:]
        WebSocketSession s = session("sess-1", null, attrs)

        when:
        handler.afterConnectionEstablished(s)

        then:
        handler.activeSessionCount() == 1
        attrs["jaiclaw.tenantId"] == "test-tenant-id-001"
    }

    def "afterConnectionClosed removes the tenant-scoped key, leaving the other tenant's session intact"() {
        given:
        WebSocketSessionHandler handler = new WebSocketSessionHandler(
                gateway, new TenantGuard(new TenantProperties(TenantMode.MULTI, "ignored", false)))
        Map<String, Object> attrsA = [:]
        Map<String, Object> attrsB = [:]
        WebSocketSession sa = session("sess-1", "tenant-a", attrsA)
        WebSocketSession sb = session("sess-1", "tenant-b", attrsB)
        handler.afterConnectionEstablished(sa)
        handler.afterConnectionEstablished(sb)

        when:
        handler.afterConnectionClosed(sa, org.springframework.web.socket.CloseStatus.NORMAL)

        then:
        !handler.hasActiveSession("tenant-a", "sess-1")
        handler.hasActiveSession("tenant-b", "sess-1")
    }

    def "storageKey helper builds tenantId:sessionKey"() {
        expect:
        WebSocketSessionHandler.storageKey("tenant-x", "sess-1") == "tenant-x:sess-1"
    }
}

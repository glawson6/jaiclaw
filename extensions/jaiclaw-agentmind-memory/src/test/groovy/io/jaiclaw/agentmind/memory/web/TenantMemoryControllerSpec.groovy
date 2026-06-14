package io.jaiclaw.agentmind.memory.web

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties
import io.jaiclaw.agentmind.memory.overflow.FailFastOverflowPolicy
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantGuard
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

import java.util.List
import java.util.Optional

class TenantMemoryControllerSpec extends Specification {

    AgentMindMemoryProvider provider = Mock()
    TenantGuard tenantGuard = Mock() { isMultiTenant() >> false }
    MemoryOverflowPolicy failFast = new FailFastOverflowPolicy()
    AgentMindMemoryProperties props = new AgentMindMemoryProperties(
            true, null,
            new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
            new AgentMindMemoryProperties.Rest(false),
            new AgentMindMemoryProperties.Tenant(true, false, List.of("ADMIN", "OPERATOR")))

    TenantMemoryController controller = new TenantMemoryController(provider, tenantGuard, failFast, props)

    HttpServletRequest reqWithRole(String role) {
        HttpServletRequest r = Mock(HttpServletRequest)
        r.isUserInRole(_) >> { String requested -> requested == role }
        return r
    }

    HttpServletRequest reqWithNoRole() {
        HttpServletRequest r = Mock(HttpServletRequest)
        r.isUserInRole(_) >> false
        return r
    }

    // ---------- read ----------

    def "read returns 200 with the persisted tenant Memory"() {
        given:
        provider.findMemory("default", MemoryScope.TENANT, null, null) >>
                Optional.of(MemoryDocument.forTenant("default", "# Outages\nSlack.", 4096))

        when:
        ResponseEntity<MemoryDocument> resp = controller.read()

        then:
        resp.statusCode == HttpStatus.OK
        resp.body.content().contains("Outages")
    }

    def "read returns 404 when no tenant Memory exists"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        ResponseEntity<MemoryDocument> resp = controller.read()

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    // ---------- upsert: role guard ----------

    def "upsert without a required role returns 403"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload("# X\ny"),
                reqWithNoRole())

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
        0 * provider.saveMemory(_)
    }

    def "upsert with ADMIN role succeeds"() {
        given:
        provider.findMemory("default", MemoryScope.TENANT, null, null) >> Optional.empty()

        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload("# X\ny"),
                reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.OK
        1 * provider.saveMemory({ MemoryDocument m ->
            m.scope() == MemoryScope.TENANT &&
                m.agentId() == null &&
                m.peerId() == null &&
                m.content().contains("X") &&
                m.charBudget() == 4096 &&
                m.version() == 0L
        }) >> { args -> args[0] }
    }

    def "upsert with OPERATOR role succeeds"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload("y"),
                reqWithRole("OPERATOR"))

        then:
        resp.statusCode == HttpStatus.OK
        1 * provider.saveMemory(_) >> { args -> args[0] }
    }

    def "upsert with an unrelated role is rejected"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload("y"),
                reqWithRole("DEVOPS"))

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
    }

    def "upsert bumps the version on an existing tenant Memory"() {
        given:
        MemoryDocument existing = new MemoryDocument(MemoryScope.TENANT, "default", null, null,
                "old", 4096, java.time.Instant.now(), 7L)
        provider.findMemory(_, _, _, _) >> Optional.of(existing)

        when:
        controller.upsert(new TenantMemoryController.TenantMemoryPayload("new"),
                reqWithRole("ADMIN"))

        then:
        1 * provider.saveMemory({ MemoryDocument m -> m.version() == 8L }) >> { args -> args[0] }
    }

    def "upsert with null content returns 400"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload(null),
                reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    def "upsert with oversize content returns 413"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()
        String tooBig = "x" * 5000  // > tenantChars budget of 4096

        when:
        ResponseEntity resp = controller.upsert(
                new TenantMemoryController.TenantMemoryPayload(tooBig),
                reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.PAYLOAD_TOO_LARGE
        0 * provider.saveMemory(_)
    }

    // ---------- delete ----------

    def "delete without a required role returns 403"() {
        when:
        ResponseEntity resp = controller.delete(reqWithNoRole())

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
        0 * provider.deleteMemory(_, _, _, _)
    }

    def "delete with ADMIN returns 204 and removes the Memory"() {
        when:
        ResponseEntity resp = controller.delete(reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.NO_CONTENT
        1 * provider.deleteMemory("default", MemoryScope.TENANT, null, null)
    }
}

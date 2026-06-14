package io.jaiclaw.hermes.soul.web

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.hermes.soul.HermesSoulProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

import java.util.List
import java.util.Optional

class TenantSoulControllerSpec extends Specification {

    SoulProvider provider = Mock()
    TenantGuard tenantGuard = Mock() {
        isMultiTenant() >> false
    }
    HermesSoulProperties props = new HermesSoulProperties(
            true, null,
            new HermesSoulProperties.Rest(false),
            new HermesSoulProperties.Tenant(true, List.of("ADMIN", "OPERATOR")))

    TenantSoulController controller = new TenantSoulController(provider, tenantGuard, props)

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

    // ---------- read (public — no role check) ----------

    def "read returns 200 with the persisted tenant Soul"() {
        given:
        provider.findSoul("default", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("default", "# Identity\nOrg voice."))

        when:
        ResponseEntity<Soul> resp = controller.read()

        then:
        resp.statusCode == HttpStatus.OK
        resp.body.markdown().contains("Org voice")
    }

    def "read returns 404 when no tenant Soul exists"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        ResponseEntity<Soul> resp = controller.read()

        then:
        resp.statusCode == HttpStatus.NOT_FOUND
    }

    // ---------- upsert: role guard ----------

    def "upsert without a required role returns 403"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantSoulController.TenantSoulPayload("# Identity\nx"),
                reqWithNoRole())

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
        0 * provider.saveSoul(_)
    }

    def "upsert with ADMIN role succeeds"() {
        given:
        provider.findSoul("default", SoulScope.TENANT, null) >> Optional.empty()

        when:
        ResponseEntity resp = controller.upsert(
                new TenantSoulController.TenantSoulPayload("# Identity\nOrg voice."),
                reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.OK
        1 * provider.saveSoul({ Soul s ->
            s.scope() == SoulScope.TENANT &&
                s.agentId() == null &&
                s.markdown().contains("Org voice") &&
                s.version() == 0L
        }) >> { Soul s -> s }
    }

    def "upsert with OPERATOR role succeeds (any role in the list passes)"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        ResponseEntity resp = controller.upsert(
                new TenantSoulController.TenantSoulPayload("body"),
                reqWithRole("OPERATOR"))

        then:
        resp.statusCode == HttpStatus.OK
        1 * provider.saveSoul(_) >> { args -> args[0] }
    }

    def "upsert with an unrelated role is rejected even if it is named close to the allowed ones"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantSoulController.TenantSoulPayload("body"),
                reqWithRole("DEVOPS"))

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
    }

    def "upsert bumps the version when a previous tenant Soul exists"() {
        given:
        Soul existing = new Soul(SoulScope.TENANT, "default", null,
                "old", java.time.Instant.now(), 7L)
        provider.findSoul(_, _, _) >> Optional.of(existing)

        when:
        controller.upsert(
                new TenantSoulController.TenantSoulPayload("new"),
                reqWithRole("ADMIN"))

        then:
        1 * provider.saveSoul({ Soul s -> s.version() == 8L }) >> { args -> args[0] }
    }

    def "upsert with null markdown returns 400"() {
        when:
        ResponseEntity resp = controller.upsert(
                new TenantSoulController.TenantSoulPayload(null),
                reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.BAD_REQUEST
    }

    // ---------- delete ----------

    def "delete without a required role returns 403"() {
        when:
        ResponseEntity resp = controller.delete(reqWithNoRole())

        then:
        resp.statusCode == HttpStatus.FORBIDDEN
        0 * provider.deleteSoul(_, _, _)
    }

    def "delete with ADMIN returns 204 and removes the Soul"() {
        when:
        ResponseEntity resp = controller.delete(reqWithRole("ADMIN"))

        then:
        resp.statusCode == HttpStatus.NO_CONTENT
        1 * provider.deleteSoul("default", SoulScope.TENANT, null)
    }
}

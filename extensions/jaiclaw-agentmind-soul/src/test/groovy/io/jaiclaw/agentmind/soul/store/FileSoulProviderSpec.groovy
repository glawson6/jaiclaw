package io.jaiclaw.agentmind.soul.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jaiclaw.core.agent.StaleSoulVersionException
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileSoulProviderSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard singleTenant = Mock() {
        isMultiTenant() >> false
    }

    TenantGuard multiTenant = Mock() {
        isMultiTenant() >> true
    }

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())

    FileSoulProvider singleProvider() { new FileSoulProvider(tmp, singleTenant, mapper) }
    FileSoulProvider multiProvider() { new FileSoulProvider(tmp, multiTenant, mapper) }

    // ---------- scope dispatch / path layout ----------

    def "SINGLE-mode TENANT Soul lands at root/TENANT-SOUL.md"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul soul = Soul.forTenant("default", "# Identity\nOrg voice.")

        when:
        p.saveSoul(soul)

        then:
        Files.exists(tmp.resolve("TENANT-SOUL.md"))
        !Files.exists(tmp.resolve("default").resolve("TENANT-SOUL.md"))
    }

    def "MULTI-mode TENANT Soul lands at root/{tenantId}/TENANT-SOUL.md"() {
        given:
        FileSoulProvider p = multiProvider()
        Soul soul = Soul.forTenant("acme", "# Identity")

        when:
        p.saveSoul(soul)

        then:
        Files.exists(tmp.resolve("acme").resolve("TENANT-SOUL.md"))
    }

    def "SINGLE-mode AGENT Soul lands under agents/{agentId}/SOUL.md"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul soul = Soul.forAgent("default", "support-bot", "# Style")

        when:
        p.saveSoul(soul)

        then:
        Files.exists(tmp.resolve("agents").resolve("support-bot").resolve("SOUL.md"))
    }

    def "MULTI-mode AGENT Soul lands under {tenantId}/agents/{agentId}/SOUL.md"() {
        given:
        FileSoulProvider p = multiProvider()
        Soul soul = Soul.forAgent("acme", "support-bot", "# Style")

        when:
        p.saveSoul(soul)

        then:
        Files.exists(tmp.resolve("acme").resolve("agents").resolve("support-bot").resolve("SOUL.md"))
    }

    def "tenant Soul and agent Soul coexist at different paths"() {
        given:
        FileSoulProvider p = multiProvider()

        when:
        p.saveSoul(Soul.forTenant("acme", "tenant md"))
        p.saveSoul(Soul.forAgent("acme", "support-bot", "agent md"))

        then:
        Files.exists(tmp.resolve("acme").resolve("TENANT-SOUL.md"))
        Files.exists(tmp.resolve("acme").resolve("agents").resolve("support-bot").resolve("SOUL.md"))
    }

    // ---------- round-trip ----------

    def "save then find round-trips markdown content"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul saved = p.saveSoul(Soul.forAgent("default", "bot", "# Identity\nHelpful."))

        when:
        Optional<Soul> loaded = p.findSoul("default", SoulScope.AGENT, "bot")

        then:
        loaded.present
        loaded.get().markdown() == "# Identity\nHelpful."
        loaded.get().scope() == SoulScope.AGENT
        loaded.get().agentId() == "bot"
        loaded.get().version() == saved.version()
    }

    def "find on a missing Soul returns empty"() {
        expect:
        singleProvider().findSoul("default", SoulScope.AGENT, "ghost").empty
    }

    // ---------- optimistic CAS ----------

    def "first write succeeds with version 0"() {
        when:
        Soul out = singleProvider().saveSoul(Soul.forAgent("default", "bot", "v0"))

        then:
        out.version() == 0L
    }

    def "writing a newer version replaces the markdown"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul v0 = p.saveSoul(Soul.forAgent("default", "bot", "v0"))

        when:
        Soul v1 = p.saveSoul(v0.withMarkdown("v1"))

        then:
        v1.markdown() == "v1"
        v1.version() == 1L
        p.findSoul("default", SoulScope.AGENT, "bot").get().markdown() == "v1"
    }

    def "writing the same version is rejected as stale"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul v0 = p.saveSoul(Soul.forAgent("default", "bot", "v0"))

        when:
        p.saveSoul(v0) // same version 0 = stale

        then:
        thrown(StaleSoulVersionException)
    }

    def "writing an older version is rejected as stale"() {
        given:
        FileSoulProvider p = singleProvider()
        Soul v0 = p.saveSoul(Soul.forAgent("default", "bot", "v0"))
        p.saveSoul(v0.withMarkdown("v1")) // store now at v1

        when:
        // attempt to push a forged v0 again — should be rejected
        p.saveSoul(v0)

        then:
        thrown(StaleSoulVersionException)
    }

    // ---------- delete ----------

    def "delete removes the on-disk file"() {
        given:
        FileSoulProvider p = singleProvider()
        p.saveSoul(Soul.forAgent("default", "bot", "x"))

        when:
        p.deleteSoul("default", SoulScope.AGENT, "bot")

        then:
        !Files.exists(tmp.resolve("agents").resolve("bot").resolve("SOUL.md"))
        p.findSoul("default", SoulScope.AGENT, "bot").empty
    }

    def "delete on a missing Soul is a no-op"() {
        when:
        singleProvider().deleteSoul("default", SoulScope.AGENT, "ghost")

        then:
        notThrown(Exception)
    }

    // ---------- isolation (cross-tenant + cross-agent reads return empty) ----------

    def "cross-tenant reads return empty"() {
        given:
        FileSoulProvider p = multiProvider()
        p.saveSoul(Soul.forAgent("tenantA", "bot", "A's voice"))

        expect:
        p.findSoul("tenantB", SoulScope.AGENT, "bot").empty
    }

    def "cross-agent reads return empty within the same tenant"() {
        given:
        FileSoulProvider p = multiProvider()
        p.saveSoul(Soul.forAgent("acme", "botA", "A"))

        expect:
        p.findSoul("acme", SoulScope.AGENT, "botB").empty
    }
}

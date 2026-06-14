package io.jaiclaw.hermes.soul.mcp

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tenant.TenantContext
import spock.lang.Specification

import java.util.Optional

class SoulMcpToolProvidersSpec extends Specification {

    SoulProvider provider = Mock()
    SoulMcpToolProvider readProv = new SoulMcpToolProvider(provider)
    TenantSoulMcpToolProvider writeProv = new TenantSoulMcpToolProvider(provider)

    TenantContext tenant(String tid) {
        TenantContext c = Mock(TenantContext)
        c.getTenantId() >> tid
        return c
    }

    // ---------- server segregation ----------

    def "read provider and tenant-admin provider use separate server names"() {
        expect:
        readProv.serverName != writeProv.serverName
        readProv.serverName == "hermes-soul"
        writeProv.serverName == "hermes-soul-tenant-admin"
    }

    def "read provider exposes only read tools — no write"() {
        expect:
        readProv.tools.collect { it.name() } as Set == ["soul_read", "soul_reflect"] as Set
    }

    def "tenant admin provider exposes only the write tool"() {
        expect:
        writeProv.tools.collect { it.name() } as Set == ["soul_tenant_write"] as Set
    }

    // ---------- soul_read ----------

    def "soul_read returns markdown for an existing AGENT Soul"() {
        given:
        provider.findSoul("acme", SoulScope.AGENT, "bot") >>
                Optional.of(Soul.forAgent("acme", "bot", "# Identity\nHelpful."))

        when:
        McpToolResult r = readProv.execute("soul_read",
                [scope: "AGENT", agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("Helpful")
    }

    def "soul_read errors when scope=AGENT without agentId"() {
        when:
        McpToolResult r = readProv.execute("soul_read", [scope: "AGENT"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("agentId")
    }

    def "soul_read returns markdown for an existing TENANT Soul"() {
        given:
        provider.findSoul("acme", SoulScope.TENANT, null) >>
                Optional.of(Soul.forTenant("acme", "# Style\nBullet points."))

        when:
        McpToolResult r = readProv.execute("soul_read", [scope: "TENANT"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("Bullet points")
    }

    def "soul_read with an unknown scope errors"() {
        when:
        McpToolResult r = readProv.execute("soul_read", [scope: "WORLD"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("Unknown scope")
    }

    def "soul_read with no scope errors"() {
        when:
        McpToolResult r = readProv.execute("soul_read", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("scope")
    }

    def "soul_read absent Soul returns error (not silent empty)"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        McpToolResult r = readProv.execute("soul_read",
                [scope: "AGENT", agentId: "ghost"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("No Soul found")
    }

    // ---------- soul_reflect ----------

    def "soul_reflect reports both scopes when agentId provided"() {
        given:
        provider.findSoul("acme", SoulScope.TENANT, null) >>
                Optional.of(new Soul(SoulScope.TENANT, "acme", null, "abc",
                        java.time.Instant.now(), 4L))
        provider.findSoul("acme", SoulScope.AGENT, "bot") >>
                Optional.of(new Soul(SoulScope.AGENT, "acme", "bot", "xyz",
                        java.time.Instant.now(), 2L))

        when:
        McpToolResult r = readProv.execute("soul_reflect", [agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("tenant: v4")
        r.content().contains("agent[bot]: v2")
    }

    def "soul_reflect reports absence for missing scopes"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        McpToolResult r = readProv.execute("soul_reflect", [agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("tenant: absent")
        r.content().contains("agent[bot]: absent")
    }

    // ---------- soul_tenant_write ----------

    def "soul_tenant_write writes a fresh TENANT Soul at version 0"() {
        given:
        provider.findSoul(_, _, _) >> Optional.empty()

        when:
        McpToolResult r = writeProv.execute("soul_tenant_write",
                [markdown: "# Identity\nOrg voice."], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("v0")
        1 * provider.saveSoul({ Soul s ->
            s.scope() == SoulScope.TENANT &&
                s.tenantId() == "acme" &&
                s.markdown().contains("Org voice") &&
                s.version() == 0L
        }) >> { args -> args[0] }
    }

    def "soul_tenant_write bumps the version on an existing tenant Soul"() {
        given:
        Soul existing = new Soul(SoulScope.TENANT, "acme", null,
                "old", java.time.Instant.now(), 11L)
        provider.findSoul(_, _, _) >> Optional.of(existing)

        when:
        McpToolResult r = writeProv.execute("soul_tenant_write",
                [markdown: "new"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("v12")
        1 * provider.saveSoul({ Soul s -> s.version() == 12L }) >> { args -> args[0] }
    }

    def "soul_tenant_write rejects missing markdown"() {
        when:
        McpToolResult r = writeProv.execute("soul_tenant_write", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("markdown")
    }

    def "soul_tenant_write rejects unknown tool name"() {
        when:
        McpToolResult r = writeProv.execute("soul_read", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("Unknown tool")
    }
}

package io.jaiclaw.agentmind.memory.mcp

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties
import io.jaiclaw.agentmind.memory.overflow.FailFastOverflowPolicy
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.mcp.McpToolResult
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantContext
import spock.lang.Specification

import java.util.List
import java.util.Optional

class MemoryMcpToolProvidersSpec extends Specification {

    AgentMindMemoryProvider provider = Mock()
    MemoryMcpToolProvider readProv = new MemoryMcpToolProvider(provider)
    AgentMindMemoryProperties props = new AgentMindMemoryProperties(
            true, null,
            new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
            new AgentMindMemoryProperties.Rest(false),
            new AgentMindMemoryProperties.Tenant(true, false, List.of("ADMIN")))
    TenantMemoryMcpToolProvider writeProv = new TenantMemoryMcpToolProvider(
            provider, new FailFastOverflowPolicy(), props)

    TenantContext tenant(String tid) {
        TenantContext c = Mock(TenantContext)
        c.getTenantId() >> tid
        return c
    }

    // ---------- server segregation ----------

    def "read and admin providers use separate server names"() {
        expect:
        readProv.serverName != writeProv.serverName
        readProv.serverName == "agentmind-memory"
        writeProv.serverName == "agentmind-memory-tenant-admin"
    }

    def "read provider exposes only read tools — no write"() {
        expect:
        readProv.tools.collect { it.name() } as Set == ["memory_read", "memory_reflect"] as Set
    }

    def "admin provider exposes only the write tool"() {
        expect:
        writeProv.tools.collect { it.name() } as Set == ["memory_tenant_write"] as Set
    }

    // ---------- memory_read ----------

    def "memory_read returns AGENT content"() {
        given:
        provider.findMemory("acme", MemoryScope.AGENT, "bot", null) >>
                Optional.of(MemoryDocument.forAgent("acme", "bot", "# Notes\nFoo.", 2200))

        when:
        McpToolResult r = readProv.execute("memory_read",
                [scope: "AGENT", agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("Foo")
    }

    def "memory_read on PEER requires agentId AND peerId"() {
        when:
        McpToolResult result1 = readProv.execute("memory_read", [scope: "PEER"], tenant("acme"))
        McpToolResult result2 = readProv.execute("memory_read",
                [scope: "PEER", agentId: "bot"], tenant("acme"))

        then:
        result1.isError()
        result1.content().contains("agentId")
        result2.isError()
        result2.content().contains("peerId")
    }

    def "memory_read on PEER with both keys succeeds"() {
        given:
        provider.findMemory("acme", MemoryScope.PEER, "bot", "U99") >>
                Optional.of(MemoryDocument.forPeer("acme", "bot", "U99", "# Bio\nBar.", 1375))

        when:
        McpToolResult r = readProv.execute("memory_read",
                [scope: "PEER", agentId: "bot", peerId: "U99"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("Bar")
    }

    def "memory_read on TENANT does not require agentId"() {
        given:
        provider.findMemory("acme", MemoryScope.TENANT, null, null) >>
                Optional.of(MemoryDocument.forTenant("acme", "# Org\nBaz.", 4096))

        when:
        McpToolResult r = readProv.execute("memory_read", [scope: "TENANT"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("Baz")
    }

    def "memory_read errors on unknown scope"() {
        when:
        McpToolResult r = readProv.execute("memory_read", [scope: "WORLD"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("Unknown scope")
    }

    def "memory_read absent returns error"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        McpToolResult r = readProv.execute("memory_read",
                [scope: "AGENT", agentId: "ghost"], tenant("acme"))

        then:
        r.isError()
        r.content().contains("No Memory found")
    }

    // ---------- memory_reflect ----------

    def "memory_reflect reports populated scopes with sizes + versions"() {
        given:
        provider.findMemory("acme", MemoryScope.TENANT, null, null) >>
                Optional.of(new MemoryDocument(MemoryScope.TENANT, "acme", null, null,
                        "x" * 100, 4096, java.time.Instant.now(), 4L))
        provider.findMemory("acme", MemoryScope.AGENT, "bot", null) >>
                Optional.of(new MemoryDocument(MemoryScope.AGENT, "acme", "bot", null,
                        "y" * 50, 2200, java.time.Instant.now(), 2L))

        when:
        McpToolResult r = readProv.execute("memory_reflect", [agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("tenant: v4")
        r.content().contains("100/4096")
        r.content().contains("agent[bot]: v2")
        r.content().contains("50/2200")
    }

    def "memory_reflect reports absence for missing scopes"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        McpToolResult r = readProv.execute("memory_reflect", [agentId: "bot"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("tenant: absent")
        r.content().contains("agent[bot]: absent")
    }

    // ---------- memory_tenant_write ----------

    def "memory_tenant_write writes a fresh TENANT Memory at version 0"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        McpToolResult r = writeProv.execute("memory_tenant_write",
                [content: "# Org\nFoo."], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("v0")
        1 * provider.saveMemory({ MemoryDocument m ->
            m.scope() == MemoryScope.TENANT &&
                m.tenantId() == "acme" &&
                m.content().contains("Foo") &&
                m.charBudget() == 4096 &&
                m.version() == 0L
        }) >> { args -> args[0] }
    }

    def "memory_tenant_write bumps the version on existing tenant Memory"() {
        given:
        MemoryDocument existing = new MemoryDocument(MemoryScope.TENANT, "acme", null, null,
                "old", 4096, java.time.Instant.now(), 11L)
        provider.findMemory(_, _, _, _) >> Optional.of(existing)

        when:
        McpToolResult r = writeProv.execute("memory_tenant_write",
                [content: "new"], tenant("acme"))

        then:
        !r.isError()
        r.content().contains("v12")
        1 * provider.saveMemory({ MemoryDocument m -> m.version() == 12L }) >> { args -> args[0] }
    }

    def "memory_tenant_write rejects missing content"() {
        when:
        McpToolResult r = writeProv.execute("memory_tenant_write", [:], tenant("acme"))

        then:
        r.isError()
        r.content().contains("content")
    }

    def "memory_tenant_write surfaces overflow as error"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        McpToolResult r = writeProv.execute("memory_tenant_write",
                [content: "x" * 5000], tenant("acme"))

        then:
        r.isError()
        r.content().contains("Overflow")
        0 * provider.saveMemory(_)
    }
}

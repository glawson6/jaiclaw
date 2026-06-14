package io.jaiclaw.agentmind.memory.tool

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties
import io.jaiclaw.agentmind.memory.overflow.FailFastOverflowPolicy
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.util.Optional

class MemoryAgentToolSpec extends Specification {

    AgentMindMemoryProvider provider = Mock()
    TenantGuard singleTenant = Mock() { isMultiTenant() >> false }
    MemoryOverflowPolicy failFast = new FailFastOverflowPolicy()
    AgentMindMemoryProperties propsAgentWriteOff = new AgentMindMemoryProperties(
            true, null,
            new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
            new AgentMindMemoryProperties.Rest(false),
            new AgentMindMemoryProperties.Tenant(true, false, List.of("ADMIN", "OPERATOR")))
    AgentMindMemoryProperties propsAgentWriteOn = new AgentMindMemoryProperties(
            true, null,
            new AgentMindMemoryProperties.Budgets(4096, 2200, 1375),
            new AgentMindMemoryProperties.Rest(false),
            new AgentMindMemoryProperties.Tenant(true, true, List.of("ADMIN", "OPERATOR")))

    MemoryAgentTool toolAgentWriteOff() {
        new MemoryAgentTool(provider, singleTenant, failFast, propsAgentWriteOff)
    }

    MemoryAgentTool toolAgentWriteOn() {
        new MemoryAgentTool(provider, singleTenant, failFast, propsAgentWriteOn)
    }

    ToolContext ctx = new ToolContext("bot", "default:slack:acct:user", "sid", ".")

    def "definition exposes memory tool name and section"() {
        expect:
        toolAgentWriteOff().definition().name() == "memory"
        toolAgentWriteOff().definition().section() == "Memory"
    }

    // ---------- AGENT scope (default) ----------

    def "add on AGENT scope creates a new Memory when none exists"() {
        given:
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >> Optional.empty()

        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "Outages", body: "Use Slack #incidents."], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveMemory({ MemoryDocument m ->
            m.scope() == MemoryScope.AGENT &&
                m.agentId() == "bot" &&
                m.peerId() == null &&
                m.content().contains("# Outages") &&
                m.charBudget() == 2200 &&
                m.version() == 0L
        }) >> { args -> args[0] }
    }

    def "add bumps the version on an existing AGENT Memory"() {
        given:
        MemoryDocument existing = new MemoryDocument(MemoryScope.AGENT, "default", "bot", null,
                "# Style\nold\n", 2200, java.time.Instant.now(), 3L)
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >> Optional.of(existing)

        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "Style", body: "new"], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveMemory({ MemoryDocument m ->
            m.content().contains("new") &&
                !m.content().contains("old") &&
                m.version() == 4L
        }) >> { args -> args[0] }
    }

    def "replace errors when the section does not exist"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "replace", heading: "Outages", body: "x"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Outages")
        0 * provider.saveMemory(_)
    }

    def "remove idempotently succeeds when the section is missing"() {
        given:
        provider.findMemory(_, _, _, _) >> Optional.empty()

        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "remove", heading: "Outages"], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("No change")
        0 * provider.saveMemory(_)
    }

    // ---------- PEER scope ----------

    def "PEER scope requires peerId parameter"() {
        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "Profile", body: "Prefers brevity.",
                 scope: "PEER"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("peerId")
        0 * provider.saveMemory(_)
    }

    def "PEER scope with peerId writes to (agent, peer) key"() {
        given:
        provider.findMemory("default", MemoryScope.PEER, "bot", "U99") >> Optional.empty()

        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "Profile", body: "Prefers brevity.",
                 scope: "PEER", peerId: "U99"], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveMemory({ MemoryDocument m ->
            m.scope() == MemoryScope.PEER &&
                m.agentId() == "bot" &&
                m.peerId() == "U99" &&
                m.charBudget() == 1375
        }) >> { args -> args[0] }
    }

    // ---------- TENANT scope: gated ----------

    def "TENANT scope is rejected when agent-write-enabled=false (default)"() {
        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "Knowledge", body: "Use Slack.",
                 scope: "TENANT"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unauthorised")
        0 * provider.findMemory(_, _, _, _)
        0 * provider.saveMemory(_)
    }

    def "TENANT scope is allowed when agent-write-enabled=true"() {
        given:
        provider.findMemory("default", MemoryScope.TENANT, null, null) >> Optional.empty()

        when:
        ToolResult result = toolAgentWriteOn().execute(
                [action: "add", heading: "Knowledge", body: "Use Slack.",
                 scope: "TENANT"], ctx)

        then:
        result instanceof ToolResult.Success
        1 * provider.saveMemory({ MemoryDocument m ->
            m.scope() == MemoryScope.TENANT &&
                m.agentId() == null &&
                m.peerId() == null &&
                m.charBudget() == 4096
        }) >> { args -> args[0] }
    }

    // ---------- overflow ----------

    def "content exceeding charBudget surfaces as ToolResult.Error (no crash)"() {
        given:
        // existing AGENT Memory already near the budget
        String filler = "x" * 2100
        MemoryDocument existing = new MemoryDocument(MemoryScope.AGENT, "default", "bot", null,
                filler, 2200, java.time.Instant.now(), 1L)
        provider.findMemory("default", MemoryScope.AGENT, "bot", null) >> Optional.of(existing)

        when:
        // adding a new section pushes content past the budget
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "New", body: "y" * 200], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Overflow")
        ((ToolResult.Error) result).message().contains("2200")
        0 * provider.saveMemory(_)
    }

    // ---------- input validation ----------

    def "missing action is rejected"() {
        when:
        ToolResult result = toolAgentWriteOff().execute([heading: "X", body: "y"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("action")
    }

    def "unknown action is rejected"() {
        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "wipe", heading: "X"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unknown action")
    }

    def "unknown scope is rejected"() {
        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "X", body: "y", scope: "GLOBAL"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unknown scope")
    }

    def "blank heading is rejected"() {
        when:
        ToolResult result = toolAgentWriteOff().execute(
                [action: "add", heading: "   ", body: "y"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("heading")
    }
}

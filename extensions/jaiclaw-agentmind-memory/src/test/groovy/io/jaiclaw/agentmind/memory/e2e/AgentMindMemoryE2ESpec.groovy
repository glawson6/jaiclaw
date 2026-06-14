package io.jaiclaw.agentmind.memory.e2e

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties
import io.jaiclaw.agentmind.memory.hook.MemoryPromptInjector
import io.jaiclaw.agentmind.memory.tool.MemoryAgentTool
import io.jaiclaw.core.agent.AgentMindMemoryProvider
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import java.nio.file.Files

/**
 * Plan §6 task 2.14 — E2E spec.
 *
 * Boots the full Spring context with the Memory pillar + tenant scope
 * both enabled and drives a realistic scenario end-to-end against the
 * JSON backend (no mocks below the autoconfig boundary):
 *
 * <ol>
 *   <li>Operator writes TENANT, AGENT, and PEER Memory through the
 *       provider (proxy for the REST/MCP write paths).</li>
 *   <li>Agent adds a section to AGENT Memory via the {@link MemoryAgentTool}
 *       — exercises the markdown editor, optimistic CAS, the
 *       InstrumentedMemoryProvider counter increment, and the no-read
 *       invariant.</li>
 *   <li>{@link MemoryPromptInjector} rewrites a {@link BeforePromptBuildEvent}
 *       and the resulting system prompt contains the TENANT block
 *       BEFORE the AGENT block BEFORE the PEER block, all spliced after
 *       the identity line and before the behaviour preamble.</li>
 *   <li>Agent tool refuses scope=TENANT when
 *       agent-write-enabled=false (default).</li>
 *   <li>Overflow surfaces as a {@link ToolResult.Error} (no crash).</li>
 * </ol>
 */
@SpringBootTest(classes = AgentMindMemoryE2ESpec.TestApp, properties = [
        "jaiclaw.agentmind.memory.enabled=true",
        "jaiclaw.agentmind.memory.tenant.enabled=true",
        "jaiclaw.agentmind.memory.root-dir=\${java.io.tmpdir}/agentmind-memory-e2e"
])
class AgentMindMemoryE2ESpec extends Specification {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {}

    @Autowired AgentMindMemoryProvider memoryProvider
    @Autowired MemoryAgentTool memoryAgentTool
    @Autowired MemoryPromptInjector memoryPromptInjector
    @Autowired AgentMindMemoryProperties props

    String identityHeader() {
        "You are JaiClaw, Personal AI assistant.\n\nRespond directly to the user."
    }

    def setup() {
        def root = java.nio.file.Path.of(props.rootDir())
        if (Files.exists(root)) {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files.&deleteIfExists)
        }
    }

    def "Spring context wires all four Memory pillar beans + tenant write surface"() {
        expect:
        memoryProvider != null
        memoryAgentTool != null
        memoryPromptInjector != null
        props.enabled()
        props.tenant().enabled()
    }

    def "operator writes TENANT + AGENT + PEER; agent adds AGENT section; injector layers them"() {
        given: "operator-authored TENANT Memory"
        memoryProvider.saveMemory(MemoryDocument.forTenant("default",
                "# Outages\nSlack #incidents.", 4096))

        and: "operator-authored PEER Memory"
        memoryProvider.saveMemory(MemoryDocument.forPeer("default", "support-bot", "U99",
                "# Bio\nLikes brevity.", 1375))

        and: "agent-authored AGENT section via the tool"
        ToolContext ctx = new ToolContext("support-bot", "support-bot:web:user:U99", "sid", ".")
        ToolResult addResult = memoryAgentTool.execute(
                [action: "add", heading: "Style", body: "Be technical."], ctx)

        when: "agent runtime fires BeforePromptBuildEvent"
        BeforePromptBuildEvent rewritten = memoryPromptInjector.rewrite(
                BeforePromptBuildEvent.of("support-bot", "support-bot:web:user:U99", identityHeader()))

        then: "agent tool wrote successfully"
        addResult instanceof ToolResult.Success

        and: "system prompt contains all three blocks in TENANT -> AGENT -> PEER order"
        rewritten != null
        String out = rewritten.systemPrompt()
        int tenantIdx = out.indexOf("Slack #incidents")
        int agentIdx = out.indexOf("Be technical")
        int peerIdx = out.indexOf("Likes brevity")
        int identityEnd = "You are JaiClaw, Personal AI assistant.\n\n".length()
        int preamble = out.indexOf("Respond directly to the user")
        identityEnd <= tenantIdx
        tenantIdx < agentIdx
        agentIdx < peerIdx
        peerIdx < preamble
    }

    def "agent tool refuses scope=TENANT when agent-write-enabled=false (default)"() {
        when:
        ToolResult result = memoryAgentTool.execute(
                [action: "add", heading: "Outages", body: "x", scope: "TENANT"],
                new ToolContext("bot", "k", "sid", "."))

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unauthorised")
    }

    def "memory tool round-trips through the JSON backend (add then remove)"() {
        given:
        ToolContext ctx = new ToolContext("bot", "bot:web:user:U1", "sid", ".")

        when:
        ToolResult added = memoryAgentTool.execute(
                [action: "add", heading: "Decisions", body: "Use Drools for rules."], ctx)
        ToolResult removed = memoryAgentTool.execute(
                [action: "remove", heading: "Decisions"], ctx)

        then:
        added instanceof ToolResult.Success
        removed instanceof ToolResult.Success
        !memoryProvider.findMemory("default", MemoryScope.AGENT, "bot", null)
                .get().content().contains("Decisions")
    }

    def "overflow surfaces as ToolResult.Error (not runtime crash)"() {
        given: "saturate AGENT Memory to near the budget"
        ToolContext ctx = new ToolContext("bot", "bot:web:user:U1", "sid", ".")
        memoryAgentTool.execute(
                [action: "add", heading: "Filler", body: "x" * 2100], ctx)

        when: "add a section that pushes over the 2200-char budget"
        ToolResult result = memoryAgentTool.execute(
                [action: "add", heading: "More", body: "y" * 200], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Overflow")
        ((ToolResult.Error) result).message().contains("2200")
    }
}

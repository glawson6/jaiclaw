package io.jaiclaw.hermes.soul.e2e

import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.model.SoulScope
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.hermes.soul.HermesSoulAutoConfiguration
import io.jaiclaw.hermes.soul.HermesSoulProperties
import io.jaiclaw.hermes.soul.hook.SoulPromptInjector
import io.jaiclaw.hermes.soul.store.HermesStoreProvider
import io.jaiclaw.hermes.soul.tool.SoulAgentTool
import io.jaiclaw.hermes.soul.user.HermesUserKeyResolver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.nio.file.Files

/**
 * Plan §5 task 1.14 — E2E spec.
 *
 * Boots the full Spring context with the Soul pillar + tenant scope both
 * enabled, then drives a realistic scenario end-to-end against the JSON
 * backend (no mocks below the autoconfig boundary):
 *
 * <ol>
 *   <li>Operator writes a TENANT-scope Soul through the
 *       {@link SoulProvider} bean (proxy for the REST/MCP write paths).</li>
 *   <li>Agent adds an AGENT-scope section via the {@link SoulAgentTool}
 *       — exercises the markdown editor + optimistic CAS + InstrumentedSoulProvider
 *       write counter.</li>
 *   <li>{@link SoulPromptInjector} rewrites a {@link BeforePromptBuildEvent}
 *       and the resulting system prompt contains the TENANT block
 *       BEFORE the AGENT block, both AFTER the identity line.</li>
 *   <li>Agent tool refuses a scope=TENANT write — operator-only invariant.</li>
 * </ol>
 *
 * <p>The fixture's tenant Soul (# Style "bullet points") and the agent
 * Soul (# Style "technical tone") deliberately collide on the {@code Style}
 * heading so the layering is observable end-to-end.
 */
@SpringBootTest(classes = HermesSoulE2ESpec.TestApp, properties = [
        "jaiclaw.hermes.soul.enabled=true",
        "jaiclaw.hermes.soul.tenant.enabled=true",
        "jaiclaw.hermes.soul.root-dir=\${java.io.tmpdir}/hermes-soul-e2e"
])
class HermesSoulE2ESpec extends Specification {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {}

    @Autowired SoulProvider soulProvider
    @Autowired SoulAgentTool soulAgentTool
    @Autowired SoulPromptInjector soulPromptInjector
    @Autowired HermesStoreProvider hermesStoreProvider
    @Autowired HermesUserKeyResolver hermesUserKeyResolver
    @Autowired HermesSoulProperties props

    String identityHeader() {
        "You are JaiClaw, Personal AI assistant.\n\nRespond directly to the user."
    }

    def setup() {
        // Reset fixture root between specs so the autoconfig binds to a
        // clean tree.
        def root = java.nio.file.Path.of(props.rootDir())
        if (Files.exists(root)) {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files.&deleteIfExists)
        }
    }

    def "Spring context wires all four pillar beans + tenant write surface"() {
        expect:
        soulProvider != null
        soulAgentTool != null
        soulPromptInjector != null
        hermesStoreProvider != null
        hermesUserKeyResolver != null
        hermesStoreProvider.type() == "json"
    }

    def "operator writes TENANT Soul, agent adds AGENT section, prompt injector layers them"() {
        given: "operator-authored tenant Soul (Style: bullet points)"
        soulProvider.saveSoul(io.jaiclaw.core.model.Soul.forTenant("default",
                "# Identity\nWe are Acme.\n\n# Style\nBullet points."))

        and: "agent-authored AGENT-scope section (Style: technical tone)"
        ToolContext ctx = new ToolContext("support-bot", "default:web:user:abc", "sid", ".")
        ToolResult addResult = soulAgentTool.execute(
                [action: "add", heading: "Style", body: "Technical tone."],
                ctx)

        when: "agent runtime fires BeforePromptBuildEvent"
        BeforePromptBuildEvent rewritten = soulPromptInjector.rewrite(
                BeforePromptBuildEvent.of("support-bot", "default:web:user:abc", identityHeader()))

        then: "agent tool wrote successfully"
        addResult instanceof ToolResult.Success

        and: "system prompt now contains BOTH blocks, tenant first"
        rewritten != null
        String out = rewritten.systemPrompt()
        int tenantIdx = out.indexOf("Bullet points")
        int agentIdx = out.indexOf("Technical tone")
        int identityEnd = "You are JaiClaw, Personal AI assistant.\n\n".length()
        identityEnd <= tenantIdx
        tenantIdx < agentIdx
        agentIdx < out.indexOf("Respond directly to the user")
    }

    def "agent tool refuses scope=TENANT — operator-only invariant"() {
        when:
        ToolResult result = soulAgentTool.execute(
                [action: "add", heading: "Identity", body: "x", scope: "TENANT"],
                new ToolContext("support-bot", "k", "sid", "."))

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("operator-only")
    }

    def "soul tool round-trips through the JSON backend (add then remove)"() {
        given:
        ToolContext ctx = new ToolContext("bot", "k", "sid", ".")

        when:
        ToolResult added = soulAgentTool.execute(
                [action: "add", heading: "Avoid", body: "Jargon."], ctx)
        ToolResult removed = soulAgentTool.execute(
                [action: "remove", heading: "Avoid"], ctx)

        then:
        added instanceof ToolResult.Success
        removed instanceof ToolResult.Success
        soulProvider.findSoul("default", SoulScope.AGENT, "bot").get()
                .markdown().contains("Avoid") == false
    }

    def "scope-absent variant: only agent Soul exists, only agent block lands"() {
        given:
        soulAgentTool.execute(
                [action: "add", heading: "Identity", body: "Agent voice only."],
                new ToolContext("solo-bot", "k", "sid", "."))

        when:
        BeforePromptBuildEvent rewritten = soulPromptInjector.rewrite(
                BeforePromptBuildEvent.of("solo-bot", "k", identityHeader()))

        then:
        rewritten != null
        rewritten.systemPrompt().contains("Agent voice only")
        !rewritten.systemPrompt().contains("Bullet points")
    }
}

package io.jaiclaw.agent.delegation.tool

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.delegation.SubAgentHandle
import io.jaiclaw.agent.delegation.SubAgentLauncher
import io.jaiclaw.agent.delegation.SubAgentRequest
import io.jaiclaw.agent.delegation.SubAgentResult
import io.jaiclaw.config.DelegationProperties
import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CompletableFuture

class DelegateToolsSpec extends Specification {

    SubAgentLauncher launcher = Mock()
    DelegationProperties props = DelegationProperties.enabledDefaults()

    private AgentRuntimeContext parentCtx(ToolProfile profile = ToolProfile.FULL) {
        AgentRuntimeContext.builder()
                .agentId("assistant")
                .sessionKey("assistant:slack:acme:C1")
                .session(Session.create("s1", "assistant:slack:acme:C1", "assistant"))
                .identity(AgentIdentity.DEFAULT)
                .toolProfile(profile)
                .workspaceDir(".")
                .build()
    }

    private ToolContext toolCtx(AgentRuntimeContext parent = parentCtx()) {
        ToolContext.builder()
                .agentId("assistant")
                .sessionKey("assistant:slack:acme:C1")
                .sessionId("s1")
                .workspaceDir(".")
                .contextData(parent == null ? [:] : [(AgentRuntime.AGENT_RUNTIME_CONTEXT_KEY): parent])
                .build()
    }

    private static SubAgentHandle handleOf(SubAgentResult result) {
        new SubAgentHandle(result.handleId(), result.sessionKey(),
                CompletableFuture.completedFuture(result), Instant.now())
    }

    // ── delegate_task ────────────────────────────────────────────────────────

    def "a completed delegation returns the child's summary"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        launcher.launch(_) >> handleOf(
                SubAgentResult.completed("h-1", "assistant:subagent:x:1", "the answer", 3))

        when:
        def result = tool.execute([goal: "research X"], toolCtx())

        then:
        result instanceof ToolResult.Success
        result.content().contains('"status":"COMPLETED"')
        result.content().contains('"handle_id":"h-1"')
        result.content().contains("the answer")
    }

    def "a refusal comes back as an error the model can act on"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        launcher.launch(_) >> handleOf(SubAgentResult.refused("Delegation depth limit reached (max 2)."))

        when:
        def result = tool.execute([goal: "go deeper"], toolCtx())

        then:
        result instanceof ToolResult.Error
        result.message().contains("depth limit")
    }

    def "a missing goal is rejected before anything is launched"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)

        when:
        def result = tool.execute(params, toolCtx())

        then:
        result instanceof ToolResult.Error
        0 * launcher.launch(_)

        where:
        params << [[:], [goal: ""], [goal: "   "]]
    }

    def "delegation is refused when no runtime context is attached"() {
        given: "a ToolContext with no AgentRuntimeContext — depth and tenant are unknowable"
        def tool = new DelegateTaskTool(launcher, props)

        when:
        def result = tool.execute([goal: "do a thing"], toolCtx(null))

        then: "refuse rather than silently spawn unbounded, untenanted work"
        result instanceof ToolResult.Error
        result.message().contains("no agent runtime context")
        0 * launcher.launch(_)
    }

    def "the requested tool profile is passed through to the launcher"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        SubAgentRequest captured = null

        when:
        tool.execute([goal: "read files", tool_profile: "CODING"], toolCtx())

        then:
        1 * launcher.launch(_) >> { SubAgentRequest r ->
            captured = r
            handleOf(SubAgentResult.completed("h", "sk", "ok", 1))
        }
        captured.toolProfile() == ToolProfile.CODING
    }

    def "an unrecognised profile falls back to the default rather than failing"() {
        given: "the launcher will narrow whatever it gets to the parent's profile anyway"
        def tool = new DelegateTaskTool(launcher, props)
        SubAgentRequest captured = null

        when:
        tool.execute([goal: "x", tool_profile: "SUPERUSER"], toolCtx())

        then:
        1 * launcher.launch(_) >> { SubAgentRequest r ->
            captured = r
            handleOf(SubAgentResult.completed("h", "sk", "ok", 1))
        }
        captured.toolProfile() == null
    }

    def "max_iterations is parsed from both number and string forms"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        SubAgentRequest captured = null

        when:
        tool.execute([goal: "x", max_iterations: raw], toolCtx())

        then:
        1 * launcher.launch(_) >> { SubAgentRequest r ->
            captured = r
            handleOf(SubAgentResult.completed("h", "sk", "ok", 1))
        }
        captured.maxIterations() == expected

        where:
        raw    | expected
        5      | 5
        "7"    | 7
        "junk" | 0
        null   | 0
    }

    def "wait=false returns a running handle instead of blocking"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        launcher.launch(_) >> new SubAgentHandle("h-2", "sk-2", new CompletableFuture<>(), Instant.now())

        when:
        def result = tool.execute([goal: "background work", wait: false], toolCtx())

        then:
        result instanceof ToolResult.Success
        result.content().contains('"status":"RUNNING"')
        result.content().contains("delegate_status")
    }

    def "a summary containing quotes and newlines is valid JSON-escaped"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)
        launcher.launch(_) >> handleOf(
                SubAgentResult.completed("h", "sk", 'he said "hi"\nthen left', 1))

        when:
        def result = tool.execute([goal: "x"], toolCtx())

        then:
        result.content().contains('\\"hi\\"')
        result.content().contains('\\n')
    }

    // ── delegate_status ──────────────────────────────────────────────────────

    def "status reports on a known handle"() {
        given:
        def tool = new DelegateStatusTool(launcher)
        launcher.find("h-1") >> Optional.of(handleOf(
                SubAgentResult.completed("h-1", "sk", "done", 2)))

        when:
        def result = tool.execute([handle_id: "h-1"], toolCtx())

        then:
        result instanceof ToolResult.Success
        result.content().contains('"status":"COMPLETED"')
        result.content().contains("done")
    }

    def "an unknown handle is an explanatory error"() {
        given:
        def tool = new DelegateStatusTool(launcher)
        launcher.find(_) >> Optional.empty()

        when:
        def result = tool.execute([handle_id: "nope"], toolCtx())

        then:
        result instanceof ToolResult.Error
        result.message().contains("No subagent found")
    }

    def "cancel delegates to the launcher"() {
        given:
        def tool = new DelegateStatusTool(launcher)
        launcher.find("h-3") >> Optional.of(handleOf(SubAgentResult.running("h-3", "sk")))

        when:
        def result = tool.execute([handle_id: "h-3", action: "cancel"], toolCtx())

        then:
        1 * launcher.cancel("h-3") >> true
        result.content().contains('"cancelled":true')
    }

    def "a missing handle_id is rejected"() {
        given:
        def tool = new DelegateStatusTool(launcher)

        when:
        def result = tool.execute([:], toolCtx())

        then:
        result instanceof ToolResult.Error
        result.message().contains("handle_id")
    }

    def "an unknown action is rejected with the valid set"() {
        given:
        def tool = new DelegateStatusTool(launcher)
        launcher.find("h") >> Optional.of(handleOf(SubAgentResult.running("h", "sk")))

        when:
        def result = tool.execute([handle_id: "h", action: "explode"], toolCtx())

        then:
        result instanceof ToolResult.Error
        result.message().contains("status, result, or cancel")
    }

    def "both tools are tagged for the delegation section so adopters can exclude them"() {
        expect:
        new DelegateTaskTool(launcher, props).definition().section() == "delegation"
        new DelegateStatusTool(launcher).definition().section() == "delegation"
    }
}

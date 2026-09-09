package io.jaiclaw.agent.e2e

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.delegation.DefaultSubAgentLauncher
import io.jaiclaw.agent.delegation.SubAgentRequest
import io.jaiclaw.agent.delegation.SubAgentStatus
import io.jaiclaw.agent.delegation.tool.DelegateStatusTool
import io.jaiclaw.agent.delegation.tool.DelegateTaskTool
import io.jaiclaw.agent.session.InMemorySessionManager
import io.jaiclaw.config.DelegationProperties
import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.SubAgentEndedEvent
import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * §5.2 row 2 of the 1.2.0 plan — delegation exercised through the model-facing
 * tool against a real launcher and a real SessionManager, with only the
 * AgentRuntime stubbed (no network).
 */
class DelegationE2ESpec extends Specification {

    AgentRuntime runtime = Mock()
    AgentHookDispatcher hooks = Mock()
    InMemorySessionManager sessions = new InMemorySessionManager(null)

    DelegationProperties props = DelegationProperties.enabledDefaults()
    DefaultSubAgentLauncher launcher

    def setup() {
        launcher = new DefaultSubAgentLauncher(runtime, sessions, props, hooks)
    }

    private static AssistantMessage reply(String t) {
        AssistantMessage.builder().id("m").content(t).build()
    }

    private AgentRuntimeContext parent(int depth = 0, ToolProfile profile = ToolProfile.FULL) {
        AgentRuntimeContext.builder()
                .agentId("assistant")
                .sessionKey("assistant:slack:acme:C1")
                .session(Session.create("s1", "assistant:slack:acme:C1", "assistant"))
                .identity(AgentIdentity.DEFAULT)
                .toolProfile(profile)
                .workspaceDir(".")
                .delegationDepth(depth)
                .build()
    }

    private ToolContext toolCtx(AgentRuntimeContext p) {
        ToolContext.builder()
                .agentId(p.agentId()).sessionKey(p.sessionKey())
                .sessionId("s1").workspaceDir(".")
                .contextData([(AgentRuntime.AGENT_RUNTIME_CONTEXT_KEY): p])
                .build()
    }

    def "the model delegates a research subtask and gets the answer back"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(
                reply("Camel 4.21 is the version in use."))
        def tool = new DelegateTaskTool(launcher, props)
        def parent = parent()

        when:
        ToolResult result = tool.execute(
                [goal: "find the Camel version", context: "check the root pom"], toolCtx(parent))

        then:
        result instanceof ToolResult.Success
        result.content().contains("Camel 4.21")
        result.content().contains('"status":"COMPLETED"')
    }

    def "the child session key is derived from the parent and is real in the SessionManager"() {
        given:
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }

        when:
        launcher.launch(new SubAgentRequest("work", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then: "the documented key format, created through the real SessionManager"
        captured.get().sessionKey() == "assistant:subagent:assistant:slack:acme:C1:1"
    }

    def "the child budget is honoured and independent of the parent's"() {
        given:
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def tool = new DelegateTaskTool(launcher, props)

        when: "the model asks for a 10-iteration child"
        tool.execute([goal: "bounded work", max_iterations: 10], toolCtx(parent()))

        then: "the child ran with its own context, not the parent's session"
        captured.get().sessionKey() != parent().sessionKey()
        captured.get().delegationDepth() == 1
    }

    def "a child's tool profile is never wider than its parent's"() {
        given:
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def tool = new DelegateTaskTool(launcher, props)

        when: "a MINIMAL parent's model asks for FULL access for its child"
        tool.execute([goal: "escalate", tool_profile: "FULL"], toolCtx(parent(0, ToolProfile.MINIMAL)))

        then: "the escalation is clamped"
        captured.get().toolProfile() == ToolProfile.MINIMAL
    }

    def "SubAgentEndedEvent is fired with the child's summary"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("all done"))

        when:
        launcher.launch(new SubAgentRequest("work", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        1 * hooks.fireVoid({
            it instanceof SubAgentEndedEvent &&
                    it.status() == "COMPLETED" &&
                    it.summary() == "all done" &&
                    it.parentSessionKey() == "assistant:slack:acme:C1"
        })
    }

    def "the tenant is visible inside the child run"() {
        given:
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        def seen = new AtomicReference<String>()
        runtime.run(_, _) >> {
            def ctx = TenantContextHolder.get()
            seen.set(ctx == null ? "<no-tenant>" : ctx.getTenantId())
            CompletableFuture.completedFuture(reply("ok"))
        }

        when:
        launcher.launch(new SubAgentRequest("scoped", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        seen.get() == "acme"

        cleanup:
        TenantContextHolder.clear()
    }

    def "delegation beyond the depth limit is refused through the tool surface"() {
        given:
        def tool = new DelegateTaskTool(launcher, props)

        when: "a child already at the max depth tries to delegate again"
        def result = tool.execute([goal: "recurse forever"], toolCtx(parent(2)))

        then: "the model is told to stop, and nothing ran"
        result instanceof ToolResult.Error
        result.message().contains("depth limit")
        0 * runtime.run(_, _)
    }

    def "a background delegation is polled to completion via delegate_status"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("background answer"))
        def task = new DelegateTaskTool(launcher, props)
        def status = new DelegateStatusTool(launcher)
        def parent = parent()

        when: "started with wait=false"
        def launched = task.execute([goal: "slow work", wait: false], toolCtx(parent))
        def handleId = (launched.content() =~ /"handle_id":"([^"]+)"/)[0][1]

        and: "polled until terminal"
        def polled = null
        for (int i = 0; i < 200; i++) {
            polled = status.execute([handle_id: handleId, action: "result"], toolCtx(parent))
            if (polled.content().contains("COMPLETED")) break
            Thread.sleep(10)
        }

        then:
        polled instanceof ToolResult.Success
        polled.content().contains("background answer")
    }

    def "child sessions are closed after the run so they do not accumulate"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("ok"))

        when:
        launcher.launch(new SubAgentRequest("work", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then: "the child session is no longer active"
        def childKey = "assistant:subagent:assistant:slack:acme:C1:1"
        !sessions.listActiveSessions().any { it.sessionKey() == childKey }
    }

    def "a failing child surfaces as a tool error rather than an exception"() {
        given:
        runtime.run(_, _) >> { throw new IllegalStateException("provider down") }
        def tool = new DelegateTaskTool(launcher, props)

        when:
        def result = tool.execute([goal: "doomed"], toolCtx(parent()))

        then:
        noExceptionThrown()
        result instanceof ToolResult.Error
        result.message().contains("provider down")
    }
}

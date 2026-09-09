package io.jaiclaw.agent.delegation

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.config.DelegationProperties
import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.SubAgentEndedEvent
import io.jaiclaw.core.hook.event.SubAgentStartedEvent
import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DefaultSubAgentLauncherSpec extends Specification {

    AgentRuntime runtime = Mock()
    SessionManager sessions = Mock()
    AgentHookDispatcher hooks = Mock()

    private static AssistantMessage reply(String text) {
        AssistantMessage.builder().id("m1").content(text).build()
    }

    private AgentRuntimeContext parent(int depth = 0, ToolProfile profile = ToolProfile.FULL) {
        AgentRuntimeContext.builder()
                .agentId("assistant")
                .sessionKey("assistant:slack:acme:C123")
                .session(Session.create("s1", "assistant:slack:acme:C123", "assistant"))
                .identity(AgentIdentity.DEFAULT)
                .toolProfile(profile)
                .workspaceDir(".")
                .delegationDepth(depth)
                .build()
    }

    private DefaultSubAgentLauncher launcher(DelegationProperties props = DelegationProperties.enabledDefaults()) {
        sessions.getOrCreate(_, _) >> { String key, String agent -> Session.create(key, key, agent) }
        new DefaultSubAgentLauncher(runtime, sessions, props, hooks)
    }

    def "child session key follows {agentId}:subagent:{parentSessionKey}:{n}"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("done"))
        def l = launcher()

        when:
        def first = l.launch(new SubAgentRequest("research X", null, parent(), null, 0, true))
        def second = l.launch(new SubAgentRequest("research Y", null, parent(), null, 0, true))

        then: "siblings get monotonic suffixes so they never collide"
        first.sessionKey() == "assistant:subagent:assistant:slack:acme:C123:1"
        second.sessionKey() == "assistant:subagent:assistant:slack:acme:C123:2"
    }

    def "a completed child returns its answer as the summary"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("the answer is 42"))
        def l = launcher()

        when:
        def result = l.launch(new SubAgentRequest("find it", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        result.status() == SubAgentStatus.COMPLETED
        result.summary() == "the answer is 42"
    }

    def "depth beyond the limit is REFUSED, never queued"() {
        given: "maxDepth 2 and a parent already at depth 2"
        def l = launcher()

        when:
        def result = l.launch(new SubAgentRequest("go deeper", null, parent(2), null, 0, true))
                .await(Duration.ofSeconds(5))

        then: "refused immediately, and the message tells the model to stop trying"
        result.status() == SubAgentStatus.REFUSED
        result.error().contains("depth limit")

        and: "no child ever ran"
        0 * runtime.run(_, _)
    }

    def "depth exactly at the limit is allowed"() {
        given: "maxDepth 2, parent at depth 1 -> child depth 2"
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("ok"))
        def l = launcher()

        when:
        def result = l.launch(new SubAgentRequest("one more hop", null, parent(1), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        result.status() == SubAgentStatus.COMPLETED
    }

    def "a child may not be granted a wider tool profile than its parent"() {
        given: "a MINIMAL parent whose child asks for FULL"
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("escalate", null, parent(0, ToolProfile.MINIMAL),
                ToolProfile.FULL, 0, true)).await(Duration.ofSeconds(5))

        then: "the request is clamped down to the parent's profile"
        captured.get().toolProfile() == ToolProfile.MINIMAL
    }

    def "a child may request a narrower profile than its parent"() {
        given:
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("read only", null, parent(0, ToolProfile.FULL),
                ToolProfile.MINIMAL, 0, true)).await(Duration.ofSeconds(5))

        then:
        captured.get().toolProfile() == ToolProfile.MINIMAL
    }

    def "profile narrowing follows the privilege ordering"() {
        expect:
        DefaultSubAgentLauncher.narrowest(requested, parent) == expected

        where:
        requested             | parent                | expected
        ToolProfile.FULL      | ToolProfile.CODING    | ToolProfile.CODING
        ToolProfile.CODING    | ToolProfile.FULL      | ToolProfile.CODING
        ToolProfile.FULL      | ToolProfile.NONE      | ToolProfile.NONE
        ToolProfile.MESSAGING | ToolProfile.CODING    | ToolProfile.MESSAGING
        ToolProfile.MINIMAL   | ToolProfile.MINIMAL   | ToolProfile.MINIMAL
        null                  | ToolProfile.CODING    | ToolProfile.CODING
    }

    def "the child inherits the parent tenant on its own thread"() {
        given: "a tenant set on the launching thread"
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))
        def seen = new AtomicReference<String>()
        runtime.run(_, _) >> {
            def ctx = TenantContextHolder.get()
            seen.set(ctx == null ? "<no-tenant>" : ctx.getTenantId())
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("scoped work", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then: "the child, on a different virtual thread, still sees the tenant"
        seen.get() == "acme"

        cleanup:
        TenantContextHolder.clear()
    }

    def "the child context carries depth and the parent session key"() {
        given:
        def captured = new AtomicReference<AgentRuntimeContext>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(ctx)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("work", null, parent(0), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        captured.get().delegationDepth() == 1
        captured.get().parentSessionKey() == "assistant:slack:acme:C123"
    }

    def "a failing child yields FAILED rather than throwing"() {
        given:
        runtime.run(_, _) >> { throw new IllegalStateException("model exploded") }
        def l = launcher()

        when:
        def result = l.launch(new SubAgentRequest("doomed", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        result.status() == SubAgentStatus.FAILED
        result.error().contains("model exploded")
    }

    def "a blank goal is refused without starting anything"() {
        given:
        def l = launcher()

        when:
        def result = l.launch(new SubAgentRequest(goal, null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        result.status() == SubAgentStatus.REFUSED
        0 * runtime.run(_, _)

        where:
        goal << [null, "", "   "]
    }

    def "lifecycle events fire for a completed child"() {
        given:
        runtime.run(_, _) >> CompletableFuture.completedFuture(reply("found it"))
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("look", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then:
        1 * hooks.fireVoid({ it instanceof SubAgentStartedEvent && it.depth() == 1 })
        1 * hooks.fireVoid({ it instanceof SubAgentEndedEvent && it.status() == "COMPLETED" })
    }

    def "an ended event fires even when the child fails"() {
        given:
        runtime.run(_, _) >> { throw new IllegalStateException("nope") }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("doomed", null, parent(), null, 0, true))
                .await(Duration.ofSeconds(5))

        then: "listeners can always close out what they opened on started"
        1 * hooks.fireVoid({ it instanceof SubAgentStartedEvent })
        1 * hooks.fireVoid({ it instanceof SubAgentEndedEvent && it.status() == "FAILED" })
    }

    def "concurrency overflow queues rather than refusing"() {
        given: "a limit of 2 and three children, the first two blocked"
        def props = new DelegationProperties(true, 2, 2, 50,
                ToolProfile.MINIMAL, Duration.ofMinutes(10), false)
        def release = new CountDownLatch(1)
        def started = new CountDownLatch(2)
        runtime.run(_, _) >> {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher(props)

        when: "two children saturate the limit"
        def a = l.launch(new SubAgentRequest("one", null, parent(), null, 0, false))
        def b = l.launch(new SubAgentRequest("two", null, parent(), null, 0, false))
        started.await(5, TimeUnit.SECONDS)
        def c = l.launch(new SubAgentRequest("three", null, parent(), null, 0, false))

        then: "the third is queued, not refused — it has a real handle and is RUNNING"
        c.poll().status() == SubAgentStatus.RUNNING
        c.id() != null

        when: "the first two finish"
        release.countDown()

        then: "the queued child eventually completes"
        c.await(Duration.ofSeconds(10)).status() == SubAgentStatus.COMPLETED
        a.await(Duration.ofSeconds(5)).status() == SubAgentStatus.COMPLETED
        b.await(Duration.ofSeconds(5)).status() == SubAgentStatus.COMPLETED
    }

    def "handles are findable and cancellable by id"() {
        given:
        def release = new CountDownLatch(1)
        runtime.run(_, _) >> {
            release.await(5, TimeUnit.SECONDS)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        def handle = l.launch(new SubAgentRequest("slow work", null, parent(), null, 0, false))

        then:
        l.find(handle.id()).isPresent()
        l.find("no-such-handle").isEmpty()

        when:
        def cancelled = l.cancel(handle.id())

        then:
        cancelled
        handle.isDone()

        cleanup:
        release.countDown()
    }

    def "a non-waiting launch returns a handle immediately"() {
        given:
        def release = new CountDownLatch(1)
        runtime.run(_, _) >> {
            release.await(5, TimeUnit.SECONDS)
            CompletableFuture.completedFuture(reply("eventually"))
        }
        def l = launcher()

        when:
        def handle = l.launch(new SubAgentRequest("background", null, parent(), null, 0, false))

        then: "the parent is not blocked"
        handle.poll().status() == SubAgentStatus.RUNNING

        cleanup:
        release.countDown()
    }

    def "the goal and parent context are combined into the child prompt"() {
        given:
        def captured = new AtomicReference<String>()
        runtime.run(_, _) >> { String input, AgentRuntimeContext ctx ->
            captured.set(input)
            CompletableFuture.completedFuture(reply("ok"))
        }
        def l = launcher()

        when:
        l.launch(new SubAgentRequest("summarise the report", "the report is at /tmp/r.pdf",
                parent(), null, 0, true)).await(Duration.ofSeconds(5))

        then:
        captured.get().contains("summarise the report")
        captured.get().contains("/tmp/r.pdf")
    }
}

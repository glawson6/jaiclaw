package io.jaiclaw.plugin

import io.jaiclaw.core.hook.HookHandler
import io.jaiclaw.core.hook.HookRegistration
import io.jaiclaw.core.hook.event.AgentEndedEvent
import io.jaiclaw.core.hook.event.AgentStartedEvent
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.hook.event.HookEvent
import io.jaiclaw.core.hook.event.LlmInputEvent
import io.jaiclaw.core.hook.event.LlmOutputEvent
import io.jaiclaw.core.hook.event.SessionStartedEvent
import io.jaiclaw.core.hook.event.ToolCallStartedEvent
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 0.8.0 hard-break: dispatch is keyed by event class rather than the
 * pre-0.8.0 {@code HookName} enum. Modifying handlers return a replacement
 * event (or null to leave unchanged). See {@code docs/MIGRATION-0.8.md} § P3.1.
 */
class HookRunnerSpec extends Specification {

    PluginRegistry registry = new PluginRegistry()
    HookRunner runner = new HookRunner(registry)

    def "fireVoid calls all handlers"() {
        given:
        def invoked = new CopyOnWriteArrayList<String>()
        registerVoidHook("p1", AgentStartedEvent.class, 100) { event ->
            invoked.add("p1"); null
        }
        registerVoidHook("p2", AgentStartedEvent.class, 100) { event ->
            invoked.add("p2"); null
        }

        when:
        runner.fireVoid(AgentStartedEvent.of("default", "sess", "hi"))

        then:
        invoked.size() == 2
        invoked.containsAll(["p1", "p2"])
    }

    def "fireVoid does not block on handler exception"() {
        given:
        def invoked = new CopyOnWriteArrayList<String>()
        registerVoidHook("failing", AgentEndedEvent.class, 50) { event ->
            throw new RuntimeException("boom")
        }
        registerVoidHook("ok", AgentEndedEvent.class, 100) { event ->
            invoked.add("ok"); null
        }

        when:
        runner.fireVoid(AgentEndedEvent.of("default", "sess", null))

        then:
        invoked.contains("ok")
    }

    def "fireVoid with no handlers is a no-op"() {
        when:
        runner.fireVoid(SessionStartedEvent.of("default", "sess"))

        then:
        noExceptionThrown()
    }

    def "fireModifying chains handlers in priority order"() {
        given:
        registerModifyingHook("p1", LlmInputEvent.class, 10) { event ->
            LlmInputEvent.of(event.agentId(), event.sessionKey(), event.userInput() + "-p1")
        }
        registerModifyingHook("p2", LlmInputEvent.class, 20) { event ->
            LlmInputEvent.of(event.agentId(), event.sessionKey(), event.userInput() + "-p2")
        }

        when:
        def result = runner.fireModifying(LlmInputEvent.of("default", "sess", "start"))

        then:
        result.userInput() == "start-p1-p2"
    }

    def "fireModifying returns original when no handlers"() {
        given:
        def original = LlmOutputEvent.of("default", "sess", "original")

        when:
        def result = runner.fireModifying(original)

        then:
        result.is(original)
    }

    def "fireModifying skips handler on exception and continues"() {
        given:
        registerModifyingHook("first", BeforePromptBuildEvent.class, 10) { event ->
            event.withSystemPrompt(event.systemPrompt() + "-first")
        }
        registerModifyingHook("broken", BeforePromptBuildEvent.class, 20) { event ->
            throw new RuntimeException("oops")
        }
        registerModifyingHook("third", BeforePromptBuildEvent.class, 30) { event ->
            event.withSystemPrompt(event.systemPrompt() + "-third")
        }

        when:
        def result = runner.fireModifying(BeforePromptBuildEvent.of("default", "sess", "start"))

        then:
        result.systemPrompt() == "start-first-third"
    }

    def "hasHandlers returns correct state"() {
        expect:
        !runner.hasHandlers(ToolCallStartedEvent.class)

        when:
        registerVoidHook("p1", ToolCallStartedEvent.class, 100) { event -> null }

        then:
        runner.hasHandlers(ToolCallStartedEvent.class)
    }

    private <E extends HookEvent> void registerVoidHook(String pluginId, Class<E> eventType, int priority, Closure handler) {
        registry.addHook(new HookRegistration(pluginId, eventType, handler as HookHandler, priority, pluginId))
    }

    private <E extends HookEvent> void registerModifyingHook(String pluginId, Class<E> eventType, int priority, Closure handler) {
        registry.addHook(new HookRegistration(pluginId, eventType, handler as HookHandler, priority, pluginId))
    }
}

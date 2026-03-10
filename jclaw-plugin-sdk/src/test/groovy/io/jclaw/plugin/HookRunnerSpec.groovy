package io.jclaw.plugin

import io.jclaw.core.hook.HookHandler
import io.jclaw.core.hook.HookName
import io.jclaw.core.hook.HookRegistration
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HookRunnerSpec extends Specification {

    PluginRegistry registry = new PluginRegistry()
    HookRunner runner = new HookRunner(registry)

    def "fireVoid calls all handlers"() {
        given:
        def invoked = new CopyOnWriteArrayList<String>()
        registerVoidHook("p1", HookName.BEFORE_AGENT_START, 100) { event, ctx ->
            invoked.add("p1")
            return null
        }
        registerVoidHook("p2", HookName.BEFORE_AGENT_START, 100) { event, ctx ->
            invoked.add("p2")
            return null
        }

        when:
        runner.fireVoid(HookName.BEFORE_AGENT_START, "event", "ctx")

        then:
        invoked.size() == 2
        invoked.containsAll(["p1", "p2"])
    }

    def "fireVoid does not block on handler exception"() {
        given:
        def invoked = new CopyOnWriteArrayList<String>()
        registerVoidHook("failing", HookName.AGENT_END, 50) { event, ctx ->
            throw new RuntimeException("boom")
        }
        registerVoidHook("ok", HookName.AGENT_END, 100) { event, ctx ->
            invoked.add("ok")
            return null
        }

        when:
        runner.fireVoid(HookName.AGENT_END, "event", "ctx")

        then:
        invoked.contains("ok")
    }

    def "fireVoid with no handlers is a no-op"() {
        when:
        runner.fireVoid(HookName.SESSION_START, "event", "ctx")

        then:
        noExceptionThrown()
    }

    def "fireModifying chains handlers in priority order"() {
        given:
        registerModifyingHook("p1", HookName.LLM_INPUT, 10) { event, ctx ->
            return event + "-p1"
        }
        registerModifyingHook("p2", HookName.LLM_INPUT, 20) { event, ctx ->
            return event + "-p2"
        }

        when:
        def result = runner.fireModifying(HookName.LLM_INPUT, "start", "ctx")

        then:
        result == "start-p1-p2"
    }

    def "fireModifying returns original when no handlers"() {
        when:
        def result = runner.fireModifying(HookName.LLM_OUTPUT, "original", "ctx")

        then:
        result == "original"
    }

    def "fireModifying skips handler on exception and continues"() {
        given:
        registerModifyingHook("first", HookName.BEFORE_PROMPT_BUILD, 10) { event, ctx ->
            return event + "-first"
        }
        registerModifyingHook("broken", HookName.BEFORE_PROMPT_BUILD, 20) { event, ctx ->
            throw new RuntimeException("oops")
        }
        registerModifyingHook("third", HookName.BEFORE_PROMPT_BUILD, 30) { event, ctx ->
            return event + "-third"
        }

        when:
        def result = runner.fireModifying(HookName.BEFORE_PROMPT_BUILD, "start", "ctx")

        then:
        result == "start-first-third"
    }

    def "hasHandlers returns correct state"() {
        expect:
        !runner.hasHandlers(HookName.BEFORE_TOOL_CALL)

        when:
        registerVoidHook("p1", HookName.BEFORE_TOOL_CALL, 100) { e, c -> null }

        then:
        runner.hasHandlers(HookName.BEFORE_TOOL_CALL)
    }

    private void registerVoidHook(String pluginId, HookName hookName, int priority, Closure handler) {
        registry.addHook(new HookRegistration(pluginId, hookName, handler as HookHandler, priority, pluginId))
    }

    private void registerModifyingHook(String pluginId, HookName hookName, int priority, Closure handler) {
        registry.addHook(new HookRegistration(pluginId, hookName, handler as HookHandler, priority, pluginId))
    }
}

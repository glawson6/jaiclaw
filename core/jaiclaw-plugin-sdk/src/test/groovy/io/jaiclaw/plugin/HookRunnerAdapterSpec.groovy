package io.jaiclaw.plugin

import io.jaiclaw.core.hook.HookName
import spock.lang.Specification

class HookRunnerAdapterSpec extends Specification {

    PluginRegistry pluginRegistry = new PluginRegistry()
    HookRunner hookRunner = new HookRunner(pluginRegistry)
    HookRunnerAdapter adapter = new HookRunnerAdapter(hookRunner)

    def "fireVoid delegates to HookRunner"() {
        when:
        adapter.fireVoid(HookName.BEFORE_AGENT_START, "event", "context")

        then:
        noExceptionThrown()
    }

    def "fireModifying returns original event when no handlers"() {
        when:
        def result = adapter.fireModifying(HookName.BEFORE_PROMPT_BUILD, "original prompt", "context")

        then:
        result == "original prompt"
    }

    def "hasHandlers returns false when no handlers registered"() {
        expect:
        !adapter.hasHandlers(HookName.BEFORE_AGENT_START)
    }
}

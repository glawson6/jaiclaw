package io.jaiclaw.plugin

import io.jaiclaw.core.hook.event.AgentStartedEvent
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import spock.lang.Specification

class HookRunnerAdapterSpec extends Specification {

    PluginRegistry pluginRegistry = new PluginRegistry()
    HookRunner hookRunner = new HookRunner(pluginRegistry)
    HookRunnerAdapter adapter = new HookRunnerAdapter(hookRunner)

    def "fireVoid delegates to HookRunner"() {
        when:
        adapter.fireVoid(AgentStartedEvent.of("default", "sess", "hi"))

        then:
        noExceptionThrown()
    }

    def "fireModifying returns original event when no handlers"() {
        given:
        def original = BeforePromptBuildEvent.of("default", "sess", "original prompt")

        when:
        def result = adapter.fireModifying(original)

        then:
        result.is(original)
    }

    def "hasHandlers returns false when no handlers registered"() {
        expect:
        !adapter.hasHandlers(AgentStartedEvent.class)
    }
}

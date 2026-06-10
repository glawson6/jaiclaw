package io.jaiclaw.plugin

import io.jaiclaw.core.hook.HookHandler
import io.jaiclaw.core.hook.HookRegistration
import io.jaiclaw.core.hook.event.AgentEndedEvent
import io.jaiclaw.core.hook.event.AgentStartedEvent
import spock.lang.Specification

class PluginRegistrySpec extends Specification {

    PluginRegistry registry = new PluginRegistry()

    def "addPlugin and retrieve"() {
        given:
        def record = new PluginRecord("p1", "Plugin One", "1.0", PluginOrigin.BUNDLED,
                true, PluginStatus.LOADED, Set.of(), Set.of())

        when:
        registry.addPlugin(record)

        then:
        registry.plugins().size() == 1
        registry.plugins()[0].id() == "p1"
    }

    def "findPlugin returns matching plugin"() {
        given:
        registry.addPlugin(new PluginRecord("p1", "One", "1.0", PluginOrigin.BUNDLED,
                true, PluginStatus.LOADED, Set.of(), Set.of()))
        registry.addPlugin(new PluginRecord("p2", "Two", "1.0", PluginOrigin.CLASSPATH,
                true, PluginStatus.LOADED, Set.of(), Set.of()))

        expect:
        registry.findPlugin("p2").isPresent()
        registry.findPlugin("p2").get().name() == "Two"
        registry.findPlugin("p3").isEmpty()
    }

    def "addHook and retrieve"() {
        given:
        HookHandler handler = { event -> null }
        def hook = new HookRegistration("p1", AgentStartedEvent.class, handler)

        when:
        registry.addHook(hook)

        then:
        registry.hooks().size() == 1
        registry.hookCount() == 1
    }

    def "hooksFor filters by event type"() {
        given:
        HookHandler h1 = { event -> null }
        HookHandler h2 = { event -> null }
        registry.addHook(new HookRegistration("p1", AgentStartedEvent.class, h1))
        registry.addHook(new HookRegistration("p2", AgentEndedEvent.class, h2))

        when:
        def startHooks = registry.hooksFor(AgentStartedEvent.class)

        then:
        startHooks.size() == 1
        startHooks[0].pluginId() == "p1"
    }

    def "pluginCount tracks total"() {
        expect:
        registry.pluginCount() == 0

        when:
        registry.addPlugin(new PluginRecord("p1", "One", "1.0", PluginOrigin.BUNDLED,
                true, PluginStatus.LOADED, Set.of(), Set.of()))

        then:
        registry.pluginCount() == 1
    }
}

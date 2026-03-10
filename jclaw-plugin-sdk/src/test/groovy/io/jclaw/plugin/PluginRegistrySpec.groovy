package io.jclaw.plugin

import io.jclaw.core.hook.HookHandler
import io.jclaw.core.hook.HookName
import io.jclaw.core.hook.HookRegistration
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
        HookHandler handler = { event, ctx -> null }
        def hook = new HookRegistration("p1", HookName.BEFORE_AGENT_START, handler)

        when:
        registry.addHook(hook)

        then:
        registry.hooks().size() == 1
        registry.hookCount() == 1
    }

    def "hooksFor filters by hook name"() {
        given:
        HookHandler h1 = { event, ctx -> null }
        HookHandler h2 = { event, ctx -> null }
        registry.addHook(new HookRegistration("p1", HookName.BEFORE_AGENT_START, h1))
        registry.addHook(new HookRegistration("p2", HookName.AGENT_END, h2))

        when:
        def startHooks = registry.hooksFor(HookName.BEFORE_AGENT_START)

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

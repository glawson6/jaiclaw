package io.jclaw.plugin

import io.jclaw.core.hook.HookName
import io.jclaw.core.plugin.PluginDefinition
import io.jclaw.core.plugin.PluginKind
import io.jclaw.core.tool.ToolCallback
import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolDefinition
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import io.jclaw.tools.ToolRegistry
import spock.lang.Specification

class PluginDiscoverySpec extends Specification {

    ToolRegistry toolRegistry = new ToolRegistry()
    PluginRegistry pluginRegistry = new PluginRegistry()
    PluginDiscovery discovery = new PluginDiscovery(toolRegistry, pluginRegistry)

    def "initializePlugin registers and activates plugin"() {
        given:
        def plugin = new TestPlugin("test-plugin", "Test Plugin", "1.0")

        when:
        def record = discovery.initializePlugin(plugin, PluginOrigin.BUNDLED)

        then:
        record != null
        record.id() == "test-plugin"
        record.status() == PluginStatus.LOADED
        record.enabled()
        record.origin() == PluginOrigin.BUNDLED
        plugin.registered
        plugin.activated
    }

    def "initializePlugin records tools registered by plugin"() {
        given:
        def plugin = new ToolRegisteringPlugin()

        when:
        def record = discovery.initializePlugin(plugin, PluginOrigin.CLASSPATH)

        then:
        record.toolNames().contains("custom_tool")
        toolRegistry.contains("custom_tool")
    }

    def "initializePlugin records hooks registered by plugin"() {
        given:
        def plugin = new HookRegisteringPlugin()

        when:
        def record = discovery.initializePlugin(plugin, PluginOrigin.CLASSPATH)

        then:
        record.hookNames().contains("BEFORE_AGENT_START")
        pluginRegistry.hookCount() == 1
    }

    def "initializePlugin handles failing plugin gracefully"() {
        given:
        def plugin = new FailingPlugin()

        when:
        def record = discovery.initializePlugin(plugin, PluginOrigin.BUNDLED)

        then:
        record != null
        record.status() == PluginStatus.ERROR
        !record.enabled()
    }

    def "initializeAll skips duplicate plugin IDs"() {
        given:
        def plugin1 = new TestPlugin("dup", "First", "1.0")
        def plugin2 = new TestPlugin("dup", "Second", "2.0")

        when:
        def records = discovery.initializeAll([plugin1, plugin2], PluginOrigin.BUNDLED)

        then:
        records.size() == 1
        records[0].name() == "First"
    }

    def "initializeAll skips already registered IDs"() {
        given:
        pluginRegistry.addPlugin(new PluginRecord("existing", "Existing", "1.0",
                PluginOrigin.BUNDLED, true, PluginStatus.LOADED, Set.of(), Set.of()))
        def plugin = new TestPlugin("existing", "Duplicate", "2.0")

        when:
        def records = discovery.initializeAll([plugin], PluginOrigin.CLASSPATH)

        then:
        records.isEmpty()
    }

    // --- Test fixtures ---

    static class TestPlugin implements JClawPlugin {
        final String id
        final String name
        final String version
        boolean registered = false
        boolean activated = false

        TestPlugin(String id, String name, String version) {
            this.id = id; this.name = name; this.version = version
        }

        @Override
        PluginDefinition definition() {
            return new PluginDefinition(id, name, "Test plugin", version, PluginKind.GENERAL)
        }

        @Override
        void register(PluginApi api) { registered = true }

        @Override
        void activate(PluginApi api) { activated = true }
    }

    static class ToolRegisteringPlugin implements JClawPlugin {
        @Override
        PluginDefinition definition() {
            return new PluginDefinition("tool-plugin", "Tool Plugin", "desc", "1.0", PluginKind.GENERAL)
        }

        @Override
        void register(PluginApi api) {
            api.registerTool(new ToolCallback() {
                @Override
                ToolDefinition definition() {
                    return new ToolDefinition("custom_tool", "A custom tool", "Custom")
                }

                @Override
                ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                    return new ToolResult.Success("ok")
                }
            })
        }
    }

    static class HookRegisteringPlugin implements JClawPlugin {
        @Override
        PluginDefinition definition() {
            return new PluginDefinition("hook-plugin", "Hook Plugin", "desc", "1.0", PluginKind.GENERAL)
        }

        @Override
        void register(PluginApi api) {
            api.on(HookName.BEFORE_AGENT_START, { event, ctx -> null })
        }
    }

    static class FailingPlugin implements JClawPlugin {
        @Override
        PluginDefinition definition() {
            return new PluginDefinition("fail-plugin", "Fail", "desc", "1.0", PluginKind.GENERAL)
        }

        @Override
        void register(PluginApi api) {
            throw new RuntimeException("Plugin init failed!")
        }
    }
}

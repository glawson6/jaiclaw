package io.jclaw.plugin;

import io.jclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Discovers and initializes plugins from multiple sources:
 * <ol>
 *   <li>Spring component scanning (auto-detected {@link JClawPlugin} beans)</li>
 *   <li>ServiceLoader discovery ({@code META-INF/services/io.jclaw.plugin.JClawPlugin})</li>
 *   <li>Explicit programmatic registration</li>
 * </ol>
 * Results are merged (deduped by plugin ID) into the PluginRegistry.
 */
public class PluginDiscovery {

    private static final Logger log = LoggerFactory.getLogger(PluginDiscovery.class);

    private final ToolRegistry toolRegistry;
    private final PluginRegistry pluginRegistry;
    private final Map<String, Object> globalPluginConfig;

    public PluginDiscovery(ToolRegistry toolRegistry, PluginRegistry pluginRegistry) {
        this(toolRegistry, pluginRegistry, Map.of());
    }

    public PluginDiscovery(ToolRegistry toolRegistry, PluginRegistry pluginRegistry,
                           Map<String, Object> globalPluginConfig) {
        this.toolRegistry = toolRegistry;
        this.pluginRegistry = pluginRegistry;
        this.globalPluginConfig = globalPluginConfig;
    }

    /**
     * Discover plugins from ServiceLoader and initialize them.
     */
    public List<PluginRecord> discoverServiceLoader() {
        var records = new ArrayList<PluginRecord>();
        var registered = registeredIds();

        ServiceLoader.load(JClawPlugin.class).forEach(plugin -> {
            var def = plugin.definition();
            if (registered.contains(def.id())) {
                log.debug("Plugin {} already registered, skipping ServiceLoader duplicate", def.id());
                return;
            }
            var record = initializePlugin(plugin, PluginOrigin.CLASSPATH);
            if (record != null) {
                records.add(record);
                registered.add(def.id());
            }
        });

        return records;
    }

    /**
     * Initialize a list of plugins (e.g., from Spring component scan).
     */
    public List<PluginRecord> initializeAll(Collection<JClawPlugin> plugins, PluginOrigin origin) {
        var records = new ArrayList<PluginRecord>();
        var registered = registeredIds();

        for (var plugin : plugins) {
            var def = plugin.definition();
            if (registered.contains(def.id())) {
                log.debug("Plugin {} already registered, skipping", def.id());
                continue;
            }
            var record = initializePlugin(plugin, origin);
            if (record != null) {
                records.add(record);
                registered.add(def.id());
            }
        }
        return records;
    }

    /**
     * Initialize a single plugin: register → record → activate.
     */
    public PluginRecord initializePlugin(JClawPlugin plugin, PluginOrigin origin) {
        var def = plugin.definition();
        try {
            var api = new PluginApiImpl(
                    def.id(), def.name(), toolRegistry, pluginRegistry, globalPluginConfig);

            plugin.register(api);
            plugin.activate(api);

            var record = new PluginRecord(
                    def.id(), def.name(), def.version(), origin,
                    true, PluginStatus.LOADED,
                    api.registeredTools(), api.registeredHooks());

            pluginRegistry.addPlugin(record);
            log.info("Plugin initialized: {} v{} [{}] (tools: {}, hooks: {})",
                    def.name(), def.version(), origin,
                    api.registeredTools().size(), api.registeredHooks().size());
            return record;
        } catch (Exception e) {
            log.error("Plugin {} failed to initialize", def.id(), e);
            var errorRecord = new PluginRecord(
                    def.id(), def.name(), def.version(), origin,
                    false, PluginStatus.ERROR, Set.of(), Set.of());
            pluginRegistry.addPlugin(errorRecord);
            return errorRecord;
        }
    }

    private Set<String> registeredIds() {
        var ids = new HashSet<String>();
        pluginRegistry.plugins().forEach(p -> ids.add(p.id()));
        return ids;
    }
}

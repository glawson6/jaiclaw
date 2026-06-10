package io.jaiclaw.autoconfigure;

import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginDiscovery;
import io.jaiclaw.plugin.PluginOrigin;
import io.jaiclaw.plugin.PluginRegistry;
import io.jaiclaw.tools.ToolRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Plugin SDK wiring.
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link PluginRegistry} — central plugin store.</li>
 *   <li>{@link PluginDiscovery} — initializes Spring-managed
 *       {@link JaiClawPlugin} beans and discovers ServiceLoader plugins.</li>
 * </ul>
 *
 * <p>Runs after {@link JaiClawToolsAutoConfiguration} so {@code ToolRegistry}
 * is available for plugins that register tools at discovery time.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawToolsAutoConfiguration.class)
public class JaiClawPluginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistry pluginRegistry() {
        return new PluginRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginDiscovery pluginDiscovery(ToolRegistry toolRegistry,
                                            PluginRegistry pluginRegistry,
                                            ObjectProvider<List<JaiClawPlugin>> pluginsProvider) {
        PluginDiscovery discovery = new PluginDiscovery(toolRegistry, pluginRegistry);

        // Initialize Spring-managed JaiClawPlugin beans
        List<JaiClawPlugin> plugins = pluginsProvider.getIfAvailable();
        if (plugins != null && !plugins.isEmpty()) {
            discovery.initializeAll(plugins, PluginOrigin.SPRING);
        }

        // Discover additional plugins via ServiceLoader
        discovery.discoverServiceLoader();

        return discovery;
    }
}

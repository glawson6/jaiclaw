package io.jaiclaw.websearch;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.websearch.provider.BraveSearchProvider;
import io.jaiclaw.websearch.provider.DuckDuckGoSearchProvider;
import io.jaiclaw.websearch.provider.TavilySearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration that creates the web search registry, registers providers,
 * and contributes a richer {@code web_search} tool that replaces the built-in
 * single-provider default.
 *
 * <p>The override works through Spring's bean-condition machinery — this
 * module declares a {@link ToolCallback} bean named {@code webSearchTool},
 * which suppresses the built-in default that
 * {@code JaiClawToolsAutoConfiguration} only contributes via
 * {@code @ConditionalOnMissingBean(name = "webSearchTool")}.
 * {@code ToolBeanDiscovery} then registers the resulting bean into
 * {@link ToolRegistry} — no manual {@code toolRegistry.register(...)} call
 * is needed.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class WebSearchAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebSearchAutoConfiguration.class);

    @Bean
    public WebSearchRegistry webSearchRegistry(Environment env) {
        var registry = new WebSearchRegistry();

        // Always register DuckDuckGo (zero-config)
        registry.register(new DuckDuckGoSearchProvider());

        // Register Brave if API key is available
        String braveKey = env.getProperty("jaiclaw.tools.web-search.brave-api-key",
                env.getProperty("BRAVE_SEARCH_API_KEY", ""));
        if (braveKey != null && !braveKey.isBlank()) {
            registry.register(new BraveSearchProvider(braveKey));
            log.info("Brave web search provider registered");
        }

        // Register Tavily if API key is available
        String tavilyKey = env.getProperty("jaiclaw.tools.web-search.tavily-api-key",
                env.getProperty("TAVILY_API_KEY", ""));
        if (tavilyKey != null && !tavilyKey.isBlank()) {
            registry.register(new TavilySearchProvider(tavilyKey));
            log.info("Tavily web search provider registered");
        }

        // Set active provider from config
        String activeProvider = env.getProperty("jaiclaw.tools.web-search.provider", "duckduckgo");
        try {
            registry.setActiveProvider(activeProvider);
        } catch (IllegalArgumentException e) {
            log.warn("Configured web search provider '{}' not available, falling back to auto-select", activeProvider);
        }

        return registry;
    }

    /**
     * Contributes a {@link RegistryWebSearchTool} as the {@code webSearchTool}
     * bean. Its presence suppresses the built-in default via
     * {@code @ConditionalOnMissingBean(name = "webSearchTool")} on the default
     * factory, and {@code ToolBeanDiscovery} registers it into the
     * {@code ToolRegistry} automatically.
     */
    @Bean(name = "webSearchTool")
    public ToolCallback webSearchTool(WebSearchRegistry registry, Environment env) {
        int maxResults = Integer.parseInt(
                env.getProperty("jaiclaw.tools.web-search.default-max-results", "5"));
        var tool = new RegistryWebSearchTool(registry, maxResults);
        log.info("RegistryWebSearchTool contributed (replaces built-in web_search), providers: {}",
                registry.providerIds());
        return tool;
    }
}

package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;

import java.util.List;

/**
 * Factory that selects the appropriate {@link SystemPromptLoader} based on the
 * strategy name in a {@link SystemPromptConfig}.
 */
public class SystemPromptLoaderFactory {

    private final List<SystemPromptLoader> loaders;

    public SystemPromptLoaderFactory() {
        this.loaders = List.of(
                new InlineSystemPromptLoader(),
                new ClasspathSystemPromptLoader(),
                new FileSystemPromptLoader(),
                new UrlSystemPromptLoader()
        );
    }

    public SystemPromptLoaderFactory(List<SystemPromptLoader> loaders) {
        this.loaders = loaders;
    }

    /**
     * Load the system prompt using the strategy specified in the config.
     *
     * @param config system prompt configuration
     * @return loaded prompt text
     * @throws SystemPromptLoadException if no loader supports the strategy or loading fails
     */
    public String load(SystemPromptConfig config) {
        if (config == null) {
            return "";
        }

        String strategy = config.strategy();
        return loaders.stream()
                .filter(loader -> loader.supports(strategy))
                .findFirst()
                .map(loader -> loader.load(config))
                .orElseThrow(() -> new SystemPromptLoadException(
                        "No system prompt loader for strategy: " + strategy));
    }
}

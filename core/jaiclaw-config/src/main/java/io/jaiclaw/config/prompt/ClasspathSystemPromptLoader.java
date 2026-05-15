package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads system prompt from a classpath resource specified by {@link SystemPromptConfig#source()}.
 *
 * <p>Uses Spring's {@link ResourceLoader} rather than constructing {@code ClassPathResource}
 * directly, so that resources resolve correctly inside Spring Boot fat JARs where classes
 * live under {@code BOOT-INF/classes/} and require the {@code LaunchedURLClassLoader}.
 */
public class ClasspathSystemPromptLoader implements SystemPromptLoader {

    private final ResourceLoader resourceLoader;

    public ClasspathSystemPromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public boolean supports(String strategy) {
        return "classpath".equalsIgnoreCase(strategy);
    }

    @Override
    public String load(SystemPromptConfig config) {
        String source = config.source();
        if (source == null || source.isBlank()) {
            throw new SystemPromptLoadException("Classpath strategy requires a 'source' path");
        }

        // Prefix with "classpath:" if no scheme is present, so ResourceLoader
        // resolves via Spring Boot's LaunchedURLClassLoader inside fat JARs
        String location = source.contains(":") ? source : "classpath:" + source;
        var resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new SystemPromptLoadException("Classpath resource not found: " + source);
        }

        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SystemPromptLoadException("Failed to read classpath resource: " + source, e);
        }
    }
}

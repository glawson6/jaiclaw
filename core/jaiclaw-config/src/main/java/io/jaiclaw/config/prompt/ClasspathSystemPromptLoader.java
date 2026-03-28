package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads system prompt from a classpath resource specified by {@link SystemPromptConfig#source()}.
 */
public class ClasspathSystemPromptLoader implements SystemPromptLoader {

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

        var resource = new ClassPathResource(source);
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

package io.jaiclaw.cli.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the system prompt from the configured location (classpath: or
 * file:), memoises it, and hands it to handlers that want to prepend
 * it to their user message.
 *
 * <p>Fail-quiet: if loading fails, logs a WARN and returns an empty
 * string so the slash command still runs — just without the guidance
 * prompt.
 */
@Component
public class SystemPromptLoader {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptLoader.class);

    private final CliGithubProperties properties;
    private final ResourceLoader resourceLoader;
    private volatile String cached;

    public SystemPromptLoader(CliGithubProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    public String load() {
        String local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = read();
            }
            return cached;
        }
    }

    private String read() {
        String location = properties.systemPromptFile();
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("System prompt not found at {} — slash commands will run without it", location);
                return "";
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            log.info("Loaded system prompt from {} ({} chars)", location, content.length());
            return content;
        } catch (IOException e) {
            log.warn("Failed to load system prompt from {}: {} — running without it",
                    location, e.getMessage());
            return "";
        }
    }
}

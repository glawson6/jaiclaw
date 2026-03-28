package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads system prompt from a filesystem path specified by {@link SystemPromptConfig#source()}.
 */
public class FileSystemPromptLoader implements SystemPromptLoader {

    @Override
    public boolean supports(String strategy) {
        return "file".equalsIgnoreCase(strategy);
    }

    @Override
    public String load(SystemPromptConfig config) {
        String source = config.source();
        if (source == null || source.isBlank()) {
            throw new SystemPromptLoadException("File strategy requires a 'source' path");
        }

        Path path = Path.of(source);
        if (!Files.exists(path)) {
            throw new SystemPromptLoadException("File not found: " + source);
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SystemPromptLoadException("Failed to read file: " + source, e);
        }
    }
}

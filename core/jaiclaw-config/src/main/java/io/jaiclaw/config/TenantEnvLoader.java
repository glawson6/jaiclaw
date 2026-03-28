package io.jaiclaw.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads per-tenant {@code .env} files following standard dotenv format:
 * {@code KEY=VALUE}, {@code #} comments, optional quoted values.
 *
 * <p>Variables are returned as a {@code Map<String, String>} for use as a
 * property source when parsing tenant YAML files (resolving {@code ${VAR}} placeholders).
 */
public class TenantEnvLoader {

    private static final Logger log = LoggerFactory.getLogger(TenantEnvLoader.class);

    private final ResourceLoader resourceLoader;

    public TenantEnvLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Load environment variables from a .env resource.
     *
     * @param resourcePath resource path (e.g., "classpath:config/tenants/acme.env" or "file:/etc/jaiclaw/tenants/acme.env")
     * @return parsed key-value pairs, empty map if resource not found
     */
    public Map<String, String> load(String resourcePath) {
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            log.debug("No .env file found at: {}", resourcePath);
            return Map.of();
        }

        Map<String, String> env = new LinkedHashMap<>();
        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                parseLine(line.trim(), env, resourcePath, lineNum);
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file at {}: {}", resourcePath, e.getMessage());
            return Map.of();
        }

        log.debug("Loaded {} env vars from {}", env.size(), resourcePath);
        return Map.copyOf(env);
    }

    private void parseLine(String line, Map<String, String> env, String path, int lineNum) {
        // Skip empty lines and comments
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }

        int eqIndex = line.indexOf('=');
        if (eqIndex < 1) {
            log.trace("Skipping invalid line {} in {}: no '=' found", lineNum, path);
            return;
        }

        String key = line.substring(0, eqIndex).trim();
        String value = line.substring(eqIndex + 1).trim();

        // Strip optional quotes (single or double)
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }

        env.put(key, value);
    }
}

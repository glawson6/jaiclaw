package io.jaiclaw.core.secrets;

import io.jaiclaw.core.api.Stable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Reads secrets from process environment variables.
 *
 * <p>This is the default provider and preserves today's {@code ${VAR}}
 * resolution behavior — adopters who set no {@code jaiclaw.secrets.*}
 * config end up with a single-provider chain containing only this.
 *
 * <p>Keys are looked up with optional name normalization: an adopter
 * may write {@code anthropic-api-key} in config but the environment
 * variable is {@code ANTHROPIC_API_KEY}. The {@code keyMapper} converts
 * the logical key to the environment-variable form. The default mapper
 * uppercases and replaces non-alphanumeric characters with underscores,
 * matching the convention used by Spring Boot's relaxed binding.
 *
 * <p>0.9.2 secrets baseline.
 */
@Stable
public final class EnvironmentSecretsProvider implements SecretsProvider {

    /** Default key mapper: upper-case, replace non-alphanumeric with {@code _}. */
    public static final UnaryOperator<String> DEFAULT_KEY_MAPPER =
            key -> key.toUpperCase().replaceAll("[^A-Z0-9]", "_");

    private final Map<String, String> environment;
    private final UnaryOperator<String> keyMapper;

    /** Build a provider backed by {@link System#getenv()}. */
    public EnvironmentSecretsProvider() {
        this(System.getenv(), DEFAULT_KEY_MAPPER);
    }

    /** Build a provider backed by a custom environment map (for testing). */
    public EnvironmentSecretsProvider(Map<String, String> environment) {
        this(environment, DEFAULT_KEY_MAPPER);
    }

    /** Build a provider with a custom key mapper (for non-standard naming). */
    public EnvironmentSecretsProvider(
            Map<String, String> environment, UnaryOperator<String> keyMapper) {
        this.environment = Map.copyOf(environment);
        this.keyMapper = keyMapper;
    }

    @Override
    public Optional<String> get(String key) {
        String envKey = keyMapper.apply(key);
        // Try the mapped form first (the convention), then the raw key
        // for cases where the env var is already in the expected shape.
        String value = environment.get(envKey);
        if (value == null) {
            value = environment.get(key);
        }
        return Optional.ofNullable(value);
    }

    @Override
    public Map<String, String> getAll(String prefix) {
        String mappedPrefix = keyMapper.apply(prefix);
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String envKey = entry.getKey();
            if (envKey.startsWith(mappedPrefix)) {
                result.put(envKey.substring(mappedPrefix.length()), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public String name() {
        return "env";
    }
}

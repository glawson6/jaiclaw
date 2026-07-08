package io.jaiclaw.blueprints;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Blueprint module configuration. Bound to {@code jaiclaw.blueprints.*}.
 *
 * @param enabled       whether the blueprint auto-configuration should run
 *                      at all (default false — opt-in like the pipeline
 *                      module)
 * @param yamlLocation  filesystem or classpath path to a directory of
 *                      YAML blueprint files. Null / blank means "no YAML
 *                      source". Java-code {@link Blueprints} beans are
 *                      always picked up regardless.
 * @param exposeMcp     whether to auto-wire {@code BlueprintMcpToolProvider}
 *                      so agents can discover blueprints. Default true so
 *                      operators just have to flip {@code enabled: true}.
 */
@ConfigurationProperties(prefix = "jaiclaw.blueprints")
public record BlueprintProperties(
        boolean enabled,
        String yamlLocation,
        boolean exposeMcp
) {
    public BlueprintProperties {
        // exposeMcp defaults to true, but Spring's ConstructorBinding will
        // pass `false` when the operator doesn't set it — override here.
    }

    public static BlueprintProperties defaults() {
        return new BlueprintProperties(false, null, true);
    }
}

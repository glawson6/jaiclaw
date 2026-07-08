package io.jaiclaw.blueprints;

import io.jaiclaw.blueprints.mcp.BlueprintMcpToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.List;

/**
 * Auto-config for the blueprints module. Opt-in via
 * {@code jaiclaw.blueprints.enabled=true} (matches the pipeline module's
 * opt-in stance). When enabled:
 *
 * <ul>
 *   <li>A {@link BlueprintRegistry} is created and populated from every
 *       {@link Blueprints} bean on the classpath.</li>
 *   <li>If {@code jaiclaw.blueprints.yaml-location} is set, YAML files in
 *       that directory are also loaded.</li>
 *   <li>Unless {@code jaiclaw.blueprints.expose-mcp=false}, a
 *       {@link BlueprintMcpToolProvider} is registered so agents can call
 *       {@code blueprints_list} and {@code blueprints_get}.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(BlueprintRegistry.class)
@ConditionalOnProperty(name = "jaiclaw.blueprints.enabled", havingValue = "true")
@EnableConfigurationProperties(BlueprintProperties.class)
public class BlueprintAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BlueprintAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public BlueprintRegistry blueprintRegistry(BlueprintProperties props,
                                                ObjectProvider<Blueprints> providers) {
        BlueprintRegistry registry = new BlueprintRegistry();

        // Java-code sources first, so their ids win over YAML on conflict.
        providers.orderedStream().forEach(p -> {
            List<BlueprintDefinition> defs = p.define();
            if (defs != null && !defs.isEmpty()) {
                registry.register(defs, "code:" + p.getClass().getSimpleName());
            }
        });

        // YAML source second.
        if (props.yamlLocation() != null && !props.yamlLocation().isBlank()) {
            Path yamlDir = Path.of(props.yamlLocation());
            List<BlueprintDefinition> yamlDefs = BlueprintYamlLoader.loadDirectory(yamlDir);
            if (!yamlDefs.isEmpty()) {
                registry.register(yamlDefs, "yaml:" + yamlDir);
            }
        }

        log.info("BlueprintRegistry initialized with {} blueprint(s) across {} categor(y|ies).",
                registry.size(), registry.categories().size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "jaiclaw.blueprints.expose-mcp", havingValue = "true", matchIfMissing = true)
    public BlueprintMcpToolProvider blueprintMcpToolProvider(BlueprintRegistry registry) {
        return new BlueprintMcpToolProvider(registry);
    }
}

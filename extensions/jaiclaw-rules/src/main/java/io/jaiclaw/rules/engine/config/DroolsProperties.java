package io.jaiclaw.rules.engine.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Drools rule engine.
 * Supports configurable rule loading from multiple sources.
 *
 * Example configuration:
 * <pre>
 * drools:
 *   rule-loaders:
 *     - type: classpath
 *       locations:
 *         - rules/**&#47;*.drl
 *       enabled: true
 *       priority: 10
 *     - type: filesystem
 *       locations:
 *         - ${RULES_DIR:/opt/rules}/**&#47;*.drl
 *       enabled: ${LOAD_FROM_FS:false}
 *       priority: 20
 *   cache-enabled: true
 *   auto-refresh: false
 *   refresh-interval-seconds: 300
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "drools")
public class DroolsProperties {

    /**
     * List of rule loader configurations.
     */
    @Valid
    @NotNull
    private List<RuleLoaderConfig> ruleLoaders = new ArrayList<>();

    /**
     * Whether to enable caching of loaded rules.
     */
    private boolean cacheEnabled = true;

    /**
     * Whether to enable automatic refresh of rules from sources.
     */
    private boolean autoRefresh = false;

    /**
     * Interval in seconds between rule refresh checks (if auto-refresh is enabled).
     */
    @Min(30)
    private int refreshIntervalSeconds = 300;

    /**
     * Whether to fail fast on rule loading errors.
     * If false, continues with available rules and logs warnings.
     */
    private boolean failFast = false;

    /**
     * Configuration for an individual rule loader.
     */
    @Data
    public static class RuleLoaderConfig {

        /**
         * Type of rule loader: classpath, filesystem, url
         */
        @NotNull
        @NotEmpty
        private String type;

        /**
         * List of locations/patterns to load rules from.
         */
        @NotNull
        @NotEmpty
        private List<String> locations = new ArrayList<>();

        /**
         * Whether this loader is enabled.
         */
        private boolean enabled = true;

        /**
         * Priority of this loader (lower values are processed first).
         */
        @Min(0)
        private int priority = 100;

        /**
         * Additional properties specific to the loader type.
         */
        private java.util.Map<String, String> properties = new java.util.HashMap<>();
    }
}

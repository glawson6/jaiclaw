package io.jaiclaw.rules.engine.loader;

import io.jaiclaw.rules.engine.config.DroolsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating RuleLoader instances based on configuration.
 */
public class RuleLoaderFactory {

    private static final Logger logger = LoggerFactory.getLogger(RuleLoaderFactory.class);

    public List<RuleLoader> createLoaders(DroolsProperties properties) throws RuleLoadingException {
        List<RuleLoader> loaders = new ArrayList<>();

        if (properties.getRuleLoaders() == null || properties.getRuleLoaders().isEmpty()) {
            logger.warn("No rule loaders configured, using default classpath loader");
            return createDefaultLoaders();
        }

        for (DroolsProperties.RuleLoaderConfig config : properties.getRuleLoaders()) {
            if (!config.isEnabled()) {
                logger.debug("Skipping disabled loader: {}", config.getType());
                continue;
            }

            try {
                RuleLoader loader = createLoader(config);
                loader.validateConfiguration();
                loaders.add(loader);
                logger.info("Configured rule loader: type={}, priority={}, locations={}",
                    loader.getLoaderType(), loader.getPriority(), loader.getLocations());
            } catch (RuleLoadingException e) {
                if (properties.isFailFast()) {
                    throw e;
                }
                logger.error("Failed to create rule loader: type={}, error={}",
                    config.getType(), e.getMessage(), e);
            }
        }

        if (loaders.isEmpty()) {
            throw new RuleLoadingException("No valid rule loaders could be created from configuration");
        }

        loaders.sort(Comparator.comparingInt(RuleLoader::getPriority));

        logger.info("Created {} rule loader(s)", loaders.size());
        return loaders;
    }

    public RuleLoader createLoader(DroolsProperties.RuleLoaderConfig config)
            throws RuleLoadingException {

        String type = config.getType().toLowerCase();
        List<String> locations = config.getLocations();
        boolean enabled = config.isEnabled();
        int priority = config.getPriority();
        Map<String, String> properties = config.getProperties();

        return switch (type) {
            case "classpath" -> new ClasspathRuleLoader(locations, enabled, priority);
            case "filesystem", "file" -> new FileSystemRuleLoader(locations, enabled, priority);
            case "url", "http", "https" -> new UrlRuleLoader(locations, enabled, priority, properties);
            default -> throw new RuleLoadingException(
                type, String.join(", ", locations), "Unsupported loader type: " + type);
        };
    }

    private List<RuleLoader> createDefaultLoaders() {
        List<String> defaultLocations = List.of("rules/**/*.drl");
        RuleLoader defaultLoader = new ClasspathRuleLoader(defaultLocations, true, 100);
        logger.info("Using default classpath rule loader with locations: {}", defaultLocations);
        return List.of(defaultLoader);
    }
}

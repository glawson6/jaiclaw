package io.jaiclaw.rules.engine.loader;

import org.kie.api.builder.KieFileSystem;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule loader implementation that loads DRL files from classpath resources.
 * Supports Ant-style path patterns (e.g., "rules/**&#47;*.drl").
 */
public class ClasspathRuleLoader extends AbstractRuleLoader {

    private static final String LOADER_TYPE = "classpath";
    private static final String CLASSPATH_PREFIX = "classpath:";

    private final ResourcePatternResolver resourceResolver;

    public ClasspathRuleLoader(List<String> locations, boolean enabled, int priority) {
        super(locations, enabled, priority);
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    public ClasspathRuleLoader(List<String> locations, boolean enabled) {
        this(locations, enabled, 100);
    }

    @Override
    public String getLoaderType() {
        return LOADER_TYPE;
    }

    @Override
    public void loadRules(KieFileSystem kieFileSystem) throws IOException, RuleLoadingException {
        if (!enabled) {
            logger.debug("Classpath rule loader is disabled, skipping");
            return;
        }

        logger.info("Loading rules from classpath locations: {}", locations);

        int totalRulesLoaded = 0;
        List<String> failedLocations = new ArrayList<>();

        for (String location : locations) {
            try {
                int rulesLoaded = loadRulesFromLocation(kieFileSystem, location);
                totalRulesLoaded += rulesLoaded;
                logger.debug("Loaded {} rule(s) from classpath location: {}", rulesLoaded, location);
            } catch (RuleLoadingException e) {
                failedLocations.add(location);
                logger.warn("Failed to load rules from classpath location: {}", location, e);
            }
        }

        if (totalRulesLoaded == 0 && !failedLocations.isEmpty()) {
            throw new RuleLoadingException(
                LOADER_TYPE,
                String.join(", ", failedLocations),
                "No rules could be loaded from any configured classpath location"
            );
        }

        logger.info("Successfully loaded {} rule file(s) from classpath", totalRulesLoaded);
    }

    private int loadRulesFromLocation(KieFileSystem kieFileSystem, String location)
            throws IOException, RuleLoadingException {

        String pattern = normalizePattern(location);
        Resource[] resources;

        try {
            resources = resourceResolver.getResources(pattern);
        } catch (IOException e) {
            throw new RuleLoadingException(
                LOADER_TYPE, location, "Failed to resolve classpath pattern: " + e.getMessage(), e);
        }

        if (resources == null || resources.length == 0) {
            logger.warn("No resources found for classpath pattern: {}", pattern);
            return 0;
        }

        int rulesLoaded = 0;
        for (int i = 0; i < resources.length; i++) {
            Resource resource = resources[i];

            if (!resource.exists() || !resource.isReadable()) {
                logger.warn("Resource not accessible: {}", resource.getDescription());
                continue;
            }

            try {
                String content = readResourceContent(resource);
                validateRuleContent(content, resource.getDescription());

                String kieResourcePath = generateKieResourcePath(location, i);
                kieFileSystem.write(kieResourcePath, content);

                logger.debug("Loaded rule file from classpath: {} -> {}",
                    resource.getDescription(), kieResourcePath);
                rulesLoaded++;

            } catch (IOException e) {
                throw new RuleLoadingException(
                    LOADER_TYPE, resource.getDescription(),
                    "Failed to read resource content: " + e.getMessage(), e);
            }
        }

        return rulesLoaded;
    }

    private String normalizePattern(String location) {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            return location;
        }
        return CLASSPATH_PREFIX + location;
    }

    private String readResourceContent(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void validateConfiguration() throws RuleLoadingException {
        super.validateConfiguration();

        for (String location : locations) {
            if (location.contains("..")) {
                throw new RuleLoadingException(
                    LOADER_TYPE, location, "Location pattern contains parent directory reference (..)");
            }
        }
    }
}

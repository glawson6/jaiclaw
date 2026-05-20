package io.jaiclaw.rules.engine.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for rule loaders providing common functionality.
 */
public abstract class AbstractRuleLoader implements RuleLoader {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final List<String> locations;
    protected final boolean enabled;
    protected final int priority;

    protected AbstractRuleLoader(List<String> locations, boolean enabled, int priority) {
        this.locations = Objects.requireNonNull(locations, "Locations cannot be null");
        this.enabled = enabled;
        this.priority = priority;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public List<String> getLocations() {
        return List.copyOf(locations);
    }

    @Override
    public void validateConfiguration() throws RuleLoadingException {
        if (locations == null || locations.isEmpty()) {
            throw new RuleLoadingException(
                getLoaderType(), "N/A", "No locations configured for rule loading");
        }

        for (String location : locations) {
            if (location == null || location.trim().isEmpty()) {
                throw new RuleLoadingException(
                    getLoaderType(), location, "Location cannot be null or empty");
            }
        }
    }

    protected void validateRuleContent(String content, String location) throws RuleLoadingException {
        if (content == null || content.trim().isEmpty()) {
            throw new RuleLoadingException(
                getLoaderType(), location, "Rule file is empty or contains only whitespace");
        }
    }

    protected String sanitizePath(String path) throws RuleLoadingException {
        if (path == null) {
            throw new RuleLoadingException(getLoaderType(), path, "Path cannot be null");
        }

        String normalizedPath = path.replace('\\', '/');

        if (normalizedPath.contains("../") || normalizedPath.contains("/..")) {
            throw new RuleLoadingException(
                getLoaderType(), path, "Path contains directory traversal attempt");
        }

        if (normalizedPath.matches(".*[A-Za-z]:\\.\\./.*")) {
            throw new RuleLoadingException(
                getLoaderType(), path, "Path contains directory traversal attempt");
        }

        return normalizedPath;
    }

    protected String generateKieResourcePath(String location, int index) {
        String fileName = extractFileName(location);
        return String.format("src/main/resources/rules/%s/%d-%s",
            getLoaderType(), index, fileName);
    }

    protected String extractFileName(String location) {
        String normalized = location.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
}

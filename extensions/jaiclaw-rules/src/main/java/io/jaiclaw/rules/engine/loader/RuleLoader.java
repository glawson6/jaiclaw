package io.jaiclaw.rules.engine.loader;

import org.kie.api.builder.KieFileSystem;

import java.io.IOException;
import java.util.List;

/**
 * Interface for loading Drools DRL rules from various sources.
 * Implementations can load rules from classpath, filesystem, URLs, databases, etc.
 */
public interface RuleLoader {

    void loadRules(KieFileSystem kieFileSystem) throws IOException, RuleLoadingException;

    String getLoaderType();

    boolean isEnabled();

    default int getPriority() {
        return 100;
    }

    List<String> getLocations();

    void validateConfiguration() throws RuleLoadingException;
}

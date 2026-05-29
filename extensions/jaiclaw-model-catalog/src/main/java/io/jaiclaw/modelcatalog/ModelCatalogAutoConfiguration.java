package io.jaiclaw.modelcatalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the model catalog. Loads default entries
 * from classpath and merges with user-provided entries.
 */
@AutoConfiguration
public class ModelCatalogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalogAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ModelCatalogLoader modelCatalogLoader() {
        return new ModelCatalogLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelCatalog modelCatalog(ModelCatalogLoader loader) {
        var catalog = new ModelCatalog();
        // Load bundled defaults
        loader.loadFromClasspath("jaiclaw-model-catalog-defaults.yml", catalog);
        // Load user overrides/additions if present
        loader.loadFromClasspath("model-catalog.yml", catalog);
        log.info("Model catalog initialized with {} entries", catalog.size());
        return catalog;
    }
}
